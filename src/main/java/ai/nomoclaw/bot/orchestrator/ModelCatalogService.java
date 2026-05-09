package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ModelCatalogService {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Duration STALE_AFTER = Duration.ofDays(30);
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(24);
    private static final String BUNDLED_RESOURCE = "model-catalog/v1/models.json";
    private static final ModelConfigDto.UploadPolicy DISABLED_UPLOAD =
            new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false);

    private final HttpClient httpClient;
    private final String remoteUrl;
    private volatile LoadedCatalog loadedCatalog;
    private volatile Instant lastRefreshAttempt = Instant.EPOCH;

    public ModelCatalogService(HttpClient appHttpClient,
                               @Value("${llm.model-catalog.remote-url:}") String remoteUrl) {
        this.httpClient = appHttpClient;
        this.remoteUrl = trim(remoteUrl);
        this.loadedCatalog = loadCatalog();
    }

    public ModelMetadata resolve(String providerId, String modelId) {
        String normalizedProvider = normalize(providerId);
        String normalizedModel = normalize(modelId);
        if (normalizedModel.isBlank()) {
            return fallback(providerId, modelId);
        }
        LoadedCatalog catalog = currentCatalog();
        CatalogModel exact = catalog.byProviderAndId.get(key(normalizedProvider, normalizedModel));
        if (exact != null) {
            return toMetadata(providerId, modelId, exact, catalog.source);
        }
        CatalogModel alias = catalog.byProviderAndAlias.get(key(normalizedProvider, normalizedModel));
        if (alias != null) {
            return toMetadata(providerId, modelId, alias, catalog.source);
        }
        CatalogModel global = catalog.byGlobalIdOrAlias.get(normalizedModel);
        if (global != null) {
            return toMetadata(providerId, modelId, global, catalog.source);
        }
        return fallback(providerId, modelId);
    }

    public ModelCatalogStatusDto getCatalogStatus() {
        LoadedCatalog catalog = currentCatalog();
        return new ModelCatalogStatusDto(
                catalog.catalog.catalogVersion(),
                catalog.catalog.generatedAt(),
                catalog.source,
                isStale(catalog.catalog.generatedAt()),
                "loaded"
        );
    }

    public ModelCatalogStatusDto refreshFromRemote() {
        if (remoteUrl.isBlank()) {
            return new ModelCatalogStatusDto(
                    currentCatalog().catalog.catalogVersion(),
                    currentCatalog().catalog.generatedAt(),
                    currentCatalog().source,
                    isStale(currentCatalog().catalog.generatedAt()),
                    "remote catalog URL is not configured"
            );
        }
        lastRefreshAttempt = Instant.now();
        try {
            String manifestBody = requestText(remoteUrl);
            JsonNode manifest = JsonUtil.fromJson(manifestBody, JsonNode.class);
            String catalogUrl = trim(manifest.path("catalogUrl").asString(""));
            String sha256 = trim(manifest.path("sha256").asString(""));
            String catalogBody = catalogUrl.isBlank() ? manifestBody : requestText(catalogUrl);
            if (!sha256.isBlank() && !sha256Hex(catalogBody).equalsIgnoreCase(sha256)) {
                throw new IllegalArgumentException("remote catalog sha256 mismatch");
            }
            CatalogFile catalog = parseCatalog(catalogBody);
            if (catalog.schemaVersion() > SUPPORTED_SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported model catalog schema version: " + catalog.schemaVersion());
            }
            Path cache = cachePath();
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, catalogBody, StandardCharsets.UTF_8);
            loadedCatalog = loadCatalog();
            return new ModelCatalogStatusDto(
                    loadedCatalog.catalog.catalogVersion(),
                    loadedCatalog.catalog.generatedAt(),
                    loadedCatalog.source,
                    isStale(loadedCatalog.catalog.generatedAt()),
                    "updated"
            );
        } catch (Exception ex) {
            log.warn("failed to refresh model catalog", ex);
            LoadedCatalog catalog = currentCatalog();
            return new ModelCatalogStatusDto(
                    catalog.catalog.catalogVersion(),
                    catalog.catalog.generatedAt(),
                    catalog.source,
                    isStale(catalog.catalog.generatedAt()),
                    "refresh failed: " + ex.getMessage()
            );
        }
    }

    public void refreshFromRemoteIfDueAsync() {
        if (remoteUrl.isBlank() || Duration.between(lastRefreshAttempt, Instant.now()).compareTo(REFRESH_INTERVAL) < 0) {
            return;
        }
        CompletableFuture.runAsync(this::refreshFromRemote);
    }

    private LoadedCatalog currentCatalog() {
        LoadedCatalog catalog = loadedCatalog;
        if (catalog == null) {
            catalog = loadCatalog();
            loadedCatalog = catalog;
        }
        return catalog;
    }

    private LoadedCatalog loadCatalog() {
        CatalogFile bundled = readBundledCatalog();
        String source = "bundled";
        CatalogFile selected = bundled;
        Optional<CatalogFile> cached = readCatalogFile(cachePath());
        if (cached.isPresent()) {
            selected = cached.get();
            source = "cache";
        }
        Optional<CatalogFile> custom = readCatalogFile(customPath());
        if (custom.isPresent()) {
            selected = mergeCatalogs(selected, custom.get());
            source = source + "+custom";
        }
        return index(selected, source);
    }

    private CatalogFile readBundledCatalog() {
        try {
            ClassPathResource resource = new ClassPathResource(BUNDLED_RESOURCE);
            try (InputStream input = resource.getInputStream()) {
                return parseCatalog(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load bundled model catalog", ex);
        }
    }

    private Optional<CatalogFile> readCatalogFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            CatalogFile catalog = parseCatalog(Files.readString(path, StandardCharsets.UTF_8));
            if (catalog.schemaVersion() > SUPPORTED_SCHEMA_VERSION) {
                log.warn("ignore unsupported model catalog {} schemaVersion={}", path, catalog.schemaVersion());
                return Optional.empty();
            }
            return Optional.of(catalog);
        } catch (Exception ex) {
            log.warn("ignore invalid model catalog {}", path, ex);
            return Optional.empty();
        }
    }

    private CatalogFile parseCatalog(String json) {
        CatalogFile catalog = JsonUtil.fromJson(json, CatalogFile.class);
        return new CatalogFile(
                catalog.schemaVersion(),
                defaultString(catalog.catalogVersion()),
                defaultString(catalog.generatedAt()),
                catalog.models() == null ? List.of() : catalog.models()
        );
    }

    private LoadedCatalog index(CatalogFile catalog, String source) {
        Map<String, CatalogModel> byProviderAndId = new LinkedHashMap<>();
        Map<String, CatalogModel> byProviderAndAlias = new LinkedHashMap<>();
        Map<String, CatalogModel> byGlobalIdOrAlias = new LinkedHashMap<>();
        for (CatalogModel model : catalog.models()) {
            String modelId = normalize(model.modelId());
            if (modelId.isBlank()) {
                continue;
            }
            List<String> providerIds = sanitize(model.providerIds());
            for (String providerId : providerIds) {
                byProviderAndId.put(key(providerId, modelId), model);
                for (String alias : sanitize(model.aliases())) {
                    byProviderAndAlias.put(key(providerId, alias), model);
                }
            }
            byGlobalIdOrAlias.putIfAbsent(modelId, model);
            for (String alias : sanitize(model.aliases())) {
                byGlobalIdOrAlias.putIfAbsent(alias, model);
            }
        }
        return new LoadedCatalog(catalog, source, byProviderAndId, byProviderAndAlias, byGlobalIdOrAlias);
    }

    private CatalogFile mergeCatalogs(CatalogFile base, CatalogFile override) {
        LinkedHashMap<String, CatalogModel> modelsByKey = new LinkedHashMap<>();
        for (CatalogModel model : base.models()) {
            modelsByKey.put(modelKey(model), model);
        }
        for (CatalogModel model : override.models()) {
            modelsByKey.put(modelKey(model), model);
        }
        return new CatalogFile(
                Math.max(base.schemaVersion(), override.schemaVersion()),
                override.catalogVersion().isBlank() ? base.catalogVersion() : override.catalogVersion(),
                override.generatedAt().isBlank() ? base.generatedAt() : override.generatedAt(),
                List.copyOf(modelsByKey.values())
        );
    }

    private ModelMetadata toMetadata(String providerId, String requestedModelId, CatalogModel model, String source) {
        CatalogUploadPolicy upload = model.uploadPolicy() == null ? CatalogUploadPolicy.disabled() : model.uploadPolicy();
        return new ModelMetadata(
                providerId,
                requestedModelId,
                trim(model.displayName()).isBlank() ? requestedModelId : trim(model.displayName()),
                sanitize(model.inputModalities()).isEmpty() ? List.of("text") : sanitize(model.inputModalities()),
                sanitize(model.outputModalities()).isEmpty() ? List.of("text") : sanitize(model.outputModalities()),
                model.reasoning(),
                sanitizeNonNegative(model.contextWindowTokens()),
                sanitizeNonNegative(model.maxInputTokens()),
                sanitizeNonNegative(model.maxOutputTokens()),
                toUploadPolicy(upload),
                true,
                source,
                trim(model.confidence()).isBlank() ? "medium" : trim(model.confidence())
        );
    }

    private ModelMetadata fallback(String providerId, String modelId) {
        return new ModelMetadata(
                providerId,
                modelId,
                trim(modelId).isBlank() ? "" : trim(modelId),
                List.of("text"),
                List.of("text"),
                false,
                0,
                0,
                0,
                DISABLED_UPLOAD,
                false,
                "fallback",
                "low"
        );
    }

    private ModelConfigDto.UploadPolicy toUploadPolicy(CatalogUploadPolicy upload) {
        return new ModelConfigDto.UploadPolicy(
                upload.enabled(),
                sanitize(upload.allowedMimeGroups()),
                sanitizeNonNegative(upload.maxFilesPerMessage()),
                sanitizeNonNegative(upload.maxImagesPerMessage()),
                sanitizeNonNegativeLong(upload.maxFileBytes()),
                sanitizeNonNegativeLong(upload.maxTotalBytes()),
                upload.singleMimeGroupOnly(),
                upload.allowMixedImageAndFile()
        );
    }

    private boolean isStale(String generatedAt) {
        try {
            return Instant.parse(generatedAt).plus(STALE_AFTER).isBefore(Instant.now());
        } catch (Exception ex) {
            return true;
        }
    }

    private String requestText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("remote catalog responded with status " + response.statusCode());
        }
        return response.body();
    }

    private String sha256Hex(String body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte item : hash) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private Path cachePath() {
        return NomoClawPaths.root().resolve("model-catalog").resolve("models.json").toAbsolutePath().normalize();
    }

    private Path customPath() {
        return NomoClawPaths.root().resolve("model-catalog").resolve("custom-models.json").toAbsolutePath().normalize();
    }

    private String modelKey(CatalogModel model) {
        return sanitize(model.providerIds()) + "::" + normalize(model.modelId());
    }

    private String key(String providerId, String modelId) {
        return normalize(providerId) + "::" + normalize(modelId);
    }

    private List<String> sanitize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private Integer sanitizeNonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private Long sanitizeNonNegativeLong(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    private String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record LoadedCatalog(
            CatalogFile catalog,
            String source,
            Map<String, CatalogModel> byProviderAndId,
            Map<String, CatalogModel> byProviderAndAlias,
            Map<String, CatalogModel> byGlobalIdOrAlias
    ) {
    }

    public record CatalogFile(
            int schemaVersion,
            String catalogVersion,
            String generatedAt,
            List<CatalogModel> models
    ) {
    }

    public record CatalogModel(
            List<String> providerIds,
            String modelId,
            String displayName,
            List<String> aliases,
            List<String> inputModalities,
            List<String> outputModalities,
            boolean reasoning,
            Integer contextWindowTokens,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            CatalogUploadPolicy uploadPolicy,
            List<String> sourceRefs,
            String lastVerifiedAt,
            String confidence
    ) {
    }

    public record CatalogUploadPolicy(
            boolean enabled,
            List<String> allowedMimeGroups,
            Integer maxFilesPerMessage,
            Integer maxImagesPerMessage,
            Long maxFileBytes,
            Long maxTotalBytes,
            boolean singleMimeGroupOnly,
            boolean allowMixedImageAndFile
    ) {
        public static CatalogUploadPolicy disabled() {
            return new CatalogUploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false);
        }
    }
}
