package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.llm.codex.CodexAuthFileTokenProvider;
import ai.nomoclaw.bot.llm.codex.CodexTokenProvider;
import ai.nomoclaw.bot.model.ModelProviderDefaults;
import ai.nomoclaw.bot.store.entity.LlmProviderConfigEntity;
import ai.nomoclaw.bot.store.entity.LlmProviderModelEntity;
import ai.nomoclaw.bot.store.repository.LlmProviderConfigRepository;
import ai.nomoclaw.bot.store.repository.LlmProviderModelRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ModelConfigAppService {

    private static final int BATCH_SIZE = 100;

    private final LlmProviderConfigRepository providerConfigRepository;
    private final LlmProviderModelRepository providerModelRepository;
    private final ModelConfigCryptoService cryptoService;
    private final ModelCatalogService modelCatalogService;
    private final HttpClient httpClient;

    public ModelConfigAppService(LlmProviderConfigRepository providerConfigRepository,
                                 LlmProviderModelRepository providerModelRepository,
                                 ModelConfigCryptoService cryptoService,
                                 ModelCatalogService modelCatalogService,
                                 HttpClient appHttpClient) {
        this.providerConfigRepository = providerConfigRepository;
        this.providerModelRepository = providerModelRepository;
        this.cryptoService = cryptoService;
        this.modelCatalogService = modelCatalogService;
        this.httpClient = appHttpClient;
    }

    @Transactional
    public ModelConfigDto getModelConfig() {
        initializeDefaultsIfNeeded();
        List<ModelConfigDto.Provider> defaults = ModelProviderDefaults.providers();
        Map<String, Integer> sortOrder = providerSortOrder(defaults);
        List<LlmProviderConfigEntity> configs = providerConfigRepository.listAll();
        List<LlmProviderModelEntity> modelEntities = providerModelRepository.listByProviderIds(
                configs.stream().map(LlmProviderConfigEntity::getProviderId).toList()
        );
        Map<String, List<LlmProviderModelEntity>> modelsByProvider = modelEntities.stream()
                .collect(Collectors.groupingBy(LlmProviderModelEntity::getProviderId, LinkedHashMap::new, Collectors.toList()));
        return new ModelConfigDto(
                configs.stream()
                        .sorted((left, right) -> Integer.compare(
                                sortOrder.getOrDefault(left.getProviderId(), Integer.MAX_VALUE),
                                sortOrder.getOrDefault(right.getProviderId(), Integer.MAX_VALUE)
                        ))
                        .map(entity -> toProviderDto(entity, modelsByProvider.getOrDefault(entity.getProviderId(), List.of())))
                        .toList()
        );
    }

    @Transactional
    public ModelConfigDto getAvailableModelConfig() {
        ModelConfigDto config = getModelConfig();
        List<ModelConfigDto.Provider> providers = config.providers().stream()
                .filter(this::isProviderConfigured)
                .map(provider -> new ModelConfigDto.Provider(
                        provider.id(),
                        provider.name(),
                        provider.protocol(),
                        provider.local(),
                        provider.requireApiKey(),
                        provider.freezeUrl(),
                        provider.baseUrl(),
                        provider.apiKey(),
                        provider.configured(),
                        provider.authStatus(),
                        provider.authMessage(),
                        provider.defaultModel(),
                        provider.models().stream()
                                .filter(model -> !trim(model.id()).isBlank())
                                .toList()
                ))
                .filter(provider -> !provider.models().isEmpty())
                .toList();
        return new ModelConfigDto(providers);
    }

    @Transactional
    public ModelConfigDto updateModelConfig(ModelConfigDto request) {
        initializeDefaultsIfNeeded();
        List<ModelConfigDto.Provider> defaults = ModelProviderDefaults.providers();
        Map<String, ModelConfigDto.Provider> requestMap = (request == null || request.providers() == null ? List.<ModelConfigDto.Provider>of() : request.providers())
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModelConfigDto.Provider::id, Function.identity(), (left, right) -> right, LinkedHashMap::new));
        List<LlmProviderConfigEntity> existingConfigs = providerConfigRepository.listByProviderIds(
                defaults.stream().map(ModelConfigDto.Provider::id).toList()
        );
        Map<String, LlmProviderConfigEntity> existingMap = existingConfigs.stream()
                .collect(Collectors.toMap(LlmProviderConfigEntity::getProviderId, Function.identity()));
        Map<String, List<LlmProviderModelEntity>> existingModelsByProvider = providerModelRepository.listByProviderIds(
                defaults.stream().map(ModelConfigDto.Provider::id).toList()
        ).stream().collect(Collectors.groupingBy(LlmProviderModelEntity::getProviderId, LinkedHashMap::new, Collectors.toList()));
        LocalDateTime now = LocalDateTime.now();
        List<LlmProviderConfigEntity> configEntities = new ArrayList<>();
        List<LlmProviderModelEntity> modelEntities = new ArrayList<>();
        for (ModelConfigDto.Provider builtin : defaults) {
            LlmProviderConfigEntity existing = existingMap.get(builtin.id());
            ModelConfigDto.Provider candidate = requestMap.get(builtin.id());
            if (candidate == null && existing != null) {
                candidate = toProviderDto(existing, existingModelsByProvider.getOrDefault(builtin.id(), List.of()));
            }
            ModelConfigDto.Provider incoming = sanitizeProvider(candidate == null ? builtin : candidate, builtin, existing);
            validateProvider(incoming);
            configEntities.add(toProviderEntity(incoming, existing, now));
            modelEntities.addAll(toModelEntities(incoming, now));
        }
        providerConfigRepository.saveOrUpdateBatch(configEntities, BATCH_SIZE);
        for (ModelConfigDto.Provider provider : defaults) {
            providerModelRepository.deleteByProviderId(provider.id());
        }
        if (!modelEntities.isEmpty()) {
            providerModelRepository.saveBatch(modelEntities, BATCH_SIZE);
        }
        return getModelConfig();
    }

    @Transactional
    public ModelConfigDto loadLocalModels(String providerId) {
        initializeDefaultsIfNeeded();
        String normalizedProviderId = trim(providerId);
        if (!"ollama".equals(normalizedProviderId)) {
            throw new IllegalArgumentException("Only ollama supports loading local models");
        }
        LlmProviderConfigEntity provider = providerConfigRepository.findByProviderId(normalizedProviderId);
        if (provider == null) {
            throw new IllegalArgumentException("provider not found: " + normalizedProviderId);
        }
        String baseUrl = trim(provider.getBaseUrl());
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL is empty");
        }
        List<String> modelIds = fetchOllamaModelIds(baseUrl);
        if (modelIds.isEmpty()) {
            throw new IllegalArgumentException("No local Ollama models found");
        }
        Map<String, LlmProviderModelEntity> existingById = providerModelRepository.listByProviderIds(List.of(normalizedProviderId)).stream()
                .collect(Collectors.toMap(LlmProviderModelEntity::getModelId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        LocalDateTime now = LocalDateTime.now();
        List<LlmProviderModelEntity> nextModels = new ArrayList<>();
        for (int i = 0; i < modelIds.size(); i++) {
            String modelId = modelIds.get(i);
            LlmProviderModelEntity entity = existingById.get(modelId);
            if (entity == null) {
                entity = new LlmProviderModelEntity();
                entity.setProviderId(normalizedProviderId);
                entity.setModelId(modelId);
                entity.setModelName(modelId);
                entity.setCapabilitiesJson(JsonUtil.toJson(List.of("text")));
                entity.setReasoning(0);
                entity.setContextWindow(0);
                entity.setMaxInputTokens(0);
                entity.setMaxOutputTokens(0);
                entity.setUploadPolicyJson(JsonUtil.toJson(new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false)));
                entity.setCreatedTime(now);
            }
            entity.setSortIndex(i);
            entity.setStatus("ACTIVE");
            entity.setUpdatedTime(now);
            nextModels.add(entity);
        }
        providerModelRepository.deleteByProviderId(normalizedProviderId);
        providerModelRepository.saveBatch(nextModels, BATCH_SIZE);

        String defaultModel = trim(provider.getDefaultModel());
        if (defaultModel.isBlank() || !modelIds.contains(defaultModel)) {
            provider.setDefaultModel(modelIds.get(0));
            provider.setUpdatedTime(now);
            providerConfigRepository.updateById(provider);
        }
        return getModelConfig();
    }

    @Transactional(readOnly = true)
    public ModelCatalogStatusDto getCatalogStatus() {
        return modelCatalogService.getCatalogStatus();
    }

    public ModelCatalogStatusDto refreshModelCatalog() {
        return modelCatalogService.refreshFromRemote();
    }

    @Transactional(readOnly = true)
    public ProbeResult testProviderConnection(String providerId, String overrideBaseUrl, String overrideApiKey) {
        ModelConfigDto config = getModelConfig();
        String normalizedProviderId = trim(providerId);
        ModelConfigDto.Provider provider = config.providers().stream()
                .filter(item -> item.id().equals(normalizedProviderId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("provider not found: " + normalizedProviderId));
        String baseUrl = trim(overrideBaseUrl).isBlank() ? trim(provider.baseUrl()) : trim(overrideBaseUrl);
        String apiKey = trim(overrideApiKey).isBlank() ? trim(provider.apiKey()) : trim(overrideApiKey);
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException(provider.name() + " 需要填写 Base URL");
        }
        if (!provider.local() && provider.requireApiKey() && apiKey.isBlank()) {
            throw new IllegalArgumentException(provider.name() + " 需要填写 API Key");
        }
        if (provider.local() || provider.id().equals("ollama")) {
            fetchOllamaModelIds(baseUrl);
            return new ProbeResult(true, "连接成功，" + provider.name() + " 可访问");
        }
        if ("codex".equals(provider.id())) {
            CodexTokenProvider.AuthStatus status = codexAuthStatus();
            if (!status.configured()) {
                throw new IllegalArgumentException(status.message());
            }
            return new ProbeResult(true, status.message());
        }
        String protocol = trim(provider.protocol()).toLowerCase(Locale.ROOT);
        if (protocol.contains("gemini")) {
            requestGeminiModels(provider.name(), baseUrl, apiKey);
            return new ProbeResult(true, "连接成功，" + provider.name() + " API 可访问");
        }
        if (protocol.contains("anthropic")) {
            requestAnthropicModels(provider.name(), baseUrl, apiKey);
            return new ProbeResult(true, "连接成功，" + provider.name() + " API 可访问");
        }
        requestOpenAiModels(provider.name(), baseUrl, apiKey);
        return new ProbeResult(true, "连接成功，" + provider.name() + " API 可访问");
    }

    public ProbeResult startCodexLogin() {
        CodexTokenProvider.AuthStatus status = codexAuthStatus();
        if (status.configured()) {
            return new ProbeResult(true, status.message());
        }
        if (!codexCliAvailable()) {
            throw new IllegalArgumentException("未找到 Codex CLI，请先安装 Codex，并在终端确认 codex login 可执行");
        }
        try {
            new ProcessBuilder("zsh", "-lc", "codex login")
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return new ProbeResult(true, "已打开 Codex 登录页面，请在浏览器完成授权后重新检测");
        } catch (Exception ex) {
            log.warn("failed to start codex login", ex);
            throw new IllegalArgumentException("无法启动 codex login，请确认 Codex CLI 已安装并在终端手动运行 codex login");
        }
    }

    private boolean codexCliAvailable() {
        try {
            Process process = new ProcessBuilder("zsh", "-lc", "command -v codex")
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            log.warn("failed to detect codex cli", ex);
            return false;
        }
    }

    private void initializeDefaultsIfNeeded() {
        List<LlmProviderConfigEntity> existingConfigs = providerConfigRepository.listAll();
        List<ModelConfigDto.Provider> defaults = ModelProviderDefaults.providers();
        if (existingConfigs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            providerConfigRepository.saveBatch(defaults.stream()
                    .map(provider -> toProviderEntity(provider, null, now))
                    .toList(), BATCH_SIZE);
            List<LlmProviderModelEntity> models = defaults.stream()
                    .map(provider -> toModelEntities(provider, now))
                    .flatMap(Collection::stream)
                    .toList();
            if (!models.isEmpty()) {
                providerModelRepository.saveBatch(models, BATCH_SIZE);
            }
            return;
        }

        Set<String> existingProviderIds = existingConfigs.stream()
                .map(LlmProviderConfigEntity::getProviderId)
                .collect(Collectors.toSet());
        List<ModelConfigDto.Provider> missingDefaults = defaults.stream()
                .filter(provider -> !existingProviderIds.contains(provider.id()))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        if (!missingDefaults.isEmpty()) {
            providerConfigRepository.saveBatch(missingDefaults.stream()
                    .map(provider -> toProviderEntity(provider, null, now))
                    .toList(), BATCH_SIZE);
            List<LlmProviderModelEntity> models = missingDefaults.stream()
                    .map(provider -> toModelEntities(provider, now))
                    .flatMap(Collection::stream)
                    .toList();
            if (!models.isEmpty()) {
                providerModelRepository.saveBatch(models, BATCH_SIZE);
            }
        }
        backfillMissingDefaultModels(defaults, now);
    }

    private void backfillMissingDefaultModels(List<ModelConfigDto.Provider> defaults, LocalDateTime now) {
        List<String> providerIds = defaults.stream()
                .map(ModelConfigDto.Provider::id)
                .toList();
        Map<String, List<LlmProviderModelEntity>> existingModelsByProvider = providerModelRepository.listByProviderIds(providerIds)
                .stream()
                .collect(Collectors.groupingBy(LlmProviderModelEntity::getProviderId, LinkedHashMap::new, Collectors.toList()));
        List<LlmProviderModelEntity> models = new ArrayList<>();
        for (ModelConfigDto.Provider provider : defaults) {
            List<LlmProviderModelEntity> existingModels = existingModelsByProvider.getOrDefault(provider.id(), List.of());
            Set<String> existingModelIds = existingModels.stream()
                    .map(LlmProviderModelEntity::getModelId)
                    .collect(Collectors.toSet());
            int sortIndex = existingModels.stream()
                    .map(LlmProviderModelEntity::getSortIndex)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .map(value -> value + 1)
                    .orElse(0);
            for (ModelConfigDto.Model model : provider.models()) {
                if (existingModelIds.contains(model.id())) {
                    continue;
                }
                models.add(toModelEntity(provider.id(), model, sortIndex++, now));
            }
        }
        if (!models.isEmpty()) {
            providerModelRepository.saveBatch(models, BATCH_SIZE);
        }
    }

    private ModelConfigDto.Provider sanitizeProvider(ModelConfigDto.Provider candidate,
                                                     ModelConfigDto.Provider builtin,
                                                     LlmProviderConfigEntity existing) {
        String baseUrl = builtin.freezeUrl()
                ? existingBaseUrlOrDefault(existing, builtin.baseUrl())
                : trim(candidate.baseUrl()).isBlank() ? builtin.baseUrl() : trim(candidate.baseUrl());
        String apiKey = trim(candidate.apiKey());
        List<ModelConfigDto.Model> sanitizedModels = sanitizeModels(candidate.models());
        String defaultModel = trim(candidate.defaultModel());
        if (defaultModel.isBlank() && !sanitizedModels.isEmpty()) {
            defaultModel = sanitizedModels.get(0).id();
        }
        return new ModelConfigDto.Provider(
                builtin.id(),
                builtin.name(),
                builtin.protocol(),
                builtin.local(),
                builtin.requireApiKey(),
                builtin.freezeUrl(),
                baseUrl,
                apiKey,
                false,
                "missing",
                "",
                defaultModel,
                sanitizedModels
        );
    }

    private String existingBaseUrlOrDefault(LlmProviderConfigEntity existing, String fallback) {
        if (existing == null || existing.getBaseUrl() == null || existing.getBaseUrl().isBlank()) {
            return fallback;
        }
        return existing.getBaseUrl();
    }

    private List<ModelConfigDto.Model> sanitizeModels(List<ModelConfigDto.Model> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, ModelConfigDto.Model> deduped = new LinkedHashMap<>();
        for (ModelConfigDto.Model model : models) {
            if (model == null) {
                continue;
            }
            String id = trim(model.id());
            if (id.isBlank()) {
                continue;
            }
            deduped.put(id, userConfiguredModel(id, trim(model.name()).isBlank() ? id : trim(model.name())));
        }
        return List.copyOf(deduped.values());
    }

    private ModelConfigDto.Model userConfiguredModel(String id, String name) {
        return new ModelConfigDto.Model(
                id,
                trim(name).isBlank() ? id : trim(name),
                List.of(),
                false,
                0,
                0,
                0,
                new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false),
                false,
                "user"
        );
    }

    private ModelConfigDto.UploadPolicy sanitizeUploadPolicy(ModelConfigDto.UploadPolicy uploadPolicy) {
        if (uploadPolicy == null) {
            return new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false);
        }
        return new ModelConfigDto.UploadPolicy(
                uploadPolicy.enabled(),
                sanitizeCapabilities(uploadPolicy.allowedMimeGroups()),
                sanitizeNonNegative(uploadPolicy.maxFilesPerMessage()),
                sanitizeNonNegative(uploadPolicy.maxImagesPerMessage()),
                sanitizeNonNegative(uploadPolicy.maxFileBytes()),
                sanitizeNonNegative(uploadPolicy.maxTotalBytes()),
                uploadPolicy.singleMimeGroupOnly(),
                uploadPolicy.allowMixedImageAndFile()
        );
    }

    private List<String> sanitizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            String value = trim(capability).toLowerCase();
            if (!value.isBlank()) {
                sanitized.add(value);
            }
        }
        return List.copyOf(sanitized);
    }

    private Integer sanitizeNonNegative(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private Long sanitizeNonNegative(Long value) {
        if (value == null || value < 0) {
            return 0L;
        }
        return value;
    }

    private void validateProvider(ModelConfigDto.Provider provider) {
        if (provider.baseUrl().isBlank()) {
            throw new IllegalArgumentException(provider.name() + " 需要填写 Base URL");
        }
        if (!provider.defaultModel().isBlank() && provider.models().stream().noneMatch(model -> model.id().equals(provider.defaultModel()))) {
            throw new IllegalArgumentException(provider.name() + " 的默认模型不在模型列表中");
        }
    }

    private boolean isProviderConfigured(ModelConfigDto.Provider provider) {
        if ("codex".equals(provider.id())) {
            return provider.configured();
        }
        if (provider.local()) {
            return !trim(provider.baseUrl()).isBlank();
        }
        return !trim(provider.apiKey()).isBlank();
    }

    private LlmProviderConfigEntity toProviderEntity(ModelConfigDto.Provider provider,
                                                     LlmProviderConfigEntity existing,
                                                     LocalDateTime now) {
        LlmProviderConfigEntity entity = existing == null ? new LlmProviderConfigEntity() : existing;
        if (entity.getCreatedTime() == null) {
            entity.setCreatedTime(now);
        }
        entity.setProviderId(provider.id());
        entity.setProviderName(provider.name());
        entity.setProtocol(provider.protocol());
        entity.setBaseUrl(provider.baseUrl());
        if (trim(provider.apiKey()).isBlank() && existing != null && !trim(existing.getApiKeyCiphertext()).isBlank()) {
            entity.setApiKeyCiphertext(existing.getApiKeyCiphertext());
        } else {
            entity.setApiKeyCiphertext(cryptoService.encrypt(provider.apiKey()));
        }
        entity.setDefaultModel(provider.defaultModel());
        entity.setLocal(provider.local() ? 1 : 0);
        entity.setRequireApiKey(provider.requireApiKey() ? 1 : 0);
        entity.setFreezeUrl(provider.freezeUrl() ? 1 : 0);
        entity.setStatus("ACTIVE");
        entity.setExtConfig("{}");
        entity.setUpdatedTime(now);
        return entity;
    }

    private List<LlmProviderModelEntity> toModelEntities(ModelConfigDto.Provider provider, LocalDateTime now) {
        List<LlmProviderModelEntity> models = new ArrayList<>();
        for (int i = 0; i < provider.models().size(); i++) {
            models.add(toModelEntity(provider.id(), provider.models().get(i), i, now));
        }
        return models;
    }

    private LlmProviderModelEntity toModelEntity(String providerId,
                                                 ModelConfigDto.Model model,
                                                 int sortIndex,
                                                 LocalDateTime now) {
        ModelMetadata metadata = modelCatalogService.resolve(providerId, model.id());
        List<String> inputModalities = metadata.matched()
                ? metadata.inputModalities()
                : sanitizeCapabilities(model.capabilities()).isEmpty() ? metadata.inputModalities() : sanitizeCapabilities(model.capabilities());
        ModelConfigDto.UploadPolicy uploadPolicy = metadata.matched()
                ? metadata.uploadPolicy()
                : model.uploadPolicy() == null ? metadata.uploadPolicy() : model.uploadPolicy();
        LlmProviderModelEntity entity = new LlmProviderModelEntity();
        entity.setProviderId(providerId);
        entity.setModelId(model.id());
        entity.setModelName(model.name());
        entity.setCapabilitiesJson(JsonUtil.toJson(inputModalities));
        entity.setReasoning((metadata.matched() ? metadata.reasoning() : model.reasoning()) ? 1 : 0);
        entity.setContextWindow(sanitizeNonNegative(metadata.matched() ? metadata.contextWindowTokens() : model.contextWindow()));
        entity.setMaxInputTokens(sanitizeNonNegative(metadata.matched() ? metadata.maxInputTokens() : model.maxInputTokens()));
        entity.setMaxOutputTokens(sanitizeNonNegative(metadata.matched() ? metadata.maxOutputTokens() : model.maxOutputTokens()));
        entity.setUploadPolicyJson(JsonUtil.toJson(sanitizeUploadPolicy(uploadPolicy)));
        entity.setSortIndex(sortIndex);
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        return entity;
    }

    private ModelConfigDto.Provider toProviderDto(LlmProviderConfigEntity config, List<LlmProviderModelEntity> models) {
        boolean configured = isStoredProviderConfigured(config);
        String authStatus = configured ? "configured" : "missing";
        String authMessage = configured ? "Provider 已配置" : "Provider 未配置";
        if ("codex".equals(config.getProviderId())) {
            CodexTokenProvider.AuthStatus status = codexAuthStatus();
            configured = status.configured();
            authStatus = status.status();
            authMessage = status.message();
        }
        return new ModelConfigDto.Provider(
                config.getProviderId(),
                config.getProviderName(),
                config.getProtocol(),
                config.getLocal() != null && config.getLocal() == 1,
                config.getRequireApiKey() != null && config.getRequireApiKey() == 1,
                config.getFreezeUrl() != null && config.getFreezeUrl() == 1,
                defaultString(config.getBaseUrl()),
                safeDecryptApiKey(config.getProviderId(), config.getApiKeyCiphertext()),
                configured,
                authStatus,
                authMessage,
                defaultString(config.getDefaultModel()),
                models.stream().map(model -> toModelDto(config.getProviderId(), model)).toList()
        );
    }

    private boolean isStoredProviderConfigured(LlmProviderConfigEntity config) {
        if (config.getLocal() != null && config.getLocal() == 1) {
            return !trim(config.getBaseUrl()).isBlank();
        }
        if (config.getRequireApiKey() != null && config.getRequireApiKey() == 0) {
            return true;
        }
        return !trim(safeDecryptApiKey(config.getProviderId(), config.getApiKeyCiphertext())).isBlank();
    }

    private CodexTokenProvider.AuthStatus codexAuthStatus() {
        return new CodexAuthFileTokenProvider(Path.of(System.getProperty("user.home"), ".codex")).authStatus();
    }

    private String safeDecryptApiKey(String providerId, String ciphertext) {
        String normalizedCiphertext = trim(ciphertext);
        if (normalizedCiphertext.isBlank()) {
            return "";
        }
        try {
            return trim(cryptoService.decrypt(normalizedCiphertext));
        } catch (Exception ex) {
            log.warn("failed to decrypt api key for provider {}, fallback to empty key", providerId, ex);
            return "";
        }
    }

    private ModelConfigDto.Model toModelDto(String providerId, LlmProviderModelEntity entity) {
        ModelMetadata metadata = modelCatalogService.resolve(providerId, entity.getModelId());
        ModelConfigDto.Model builtin = builtinModel(providerId, entity.getModelId());
        String name = trim(entity.getModelName()).isBlank()
                ? trim(metadata.displayName()).isBlank() ? entity.getModelId() : metadata.displayName()
                : entity.getModelName();
        List<String> capabilities = metadata.matched()
                ? metadata.inputModalities()
                : builtin == null ? storedCapabilities(entity) : builtin.capabilities();
        ModelConfigDto.UploadPolicy uploadPolicy = metadata.matched()
                ? metadata.uploadPolicy()
                : builtin == null ? storedUploadPolicy(entity) : builtin.uploadPolicy();
        return new ModelConfigDto.Model(
                entity.getModelId(),
                name,
                capabilities,
                metadata.matched() ? metadata.reasoning() : builtin == null ? entity.getReasoning() != null && entity.getReasoning() == 1 : builtin.reasoning(),
                metadata.matched() ? metadata.contextWindowTokens() : builtin == null ? sanitizeNonNegative(entity.getContextWindow()) : builtin.contextWindow(),
                metadata.matched() ? metadata.maxInputTokens() : builtin == null ? sanitizeNonNegative(entity.getMaxInputTokens()) : builtin.maxInputTokens(),
                metadata.matched() ? metadata.maxOutputTokens() : builtin == null ? sanitizeNonNegative(entity.getMaxOutputTokens()) : builtin.maxOutputTokens(),
                uploadPolicy,
                metadata.matched(),
                metadata.source()
        );
    }

    private ModelConfigDto.Model builtinModel(String providerId, String modelId) {
        for (ModelConfigDto.Provider provider : ModelProviderDefaults.providers()) {
            if (!provider.id().equals(trim(providerId))) {
                continue;
            }
            for (ModelConfigDto.Model model : provider.models()) {
                if (model.id().equals(trim(modelId))) {
                    return model;
                }
            }
        }
        return null;
    }

    private List<String> storedCapabilities(LlmProviderModelEntity entity) {
        List<String> capabilities = JsonUtil.fromJsonQuietly(
                defaultString(entity.getCapabilitiesJson()),
                new TypeReference<List<String>>() {}
        ).orElse(List.of());
        List<String> sanitized = sanitizeCapabilities(capabilities);
        return sanitized.isEmpty() ? List.of("text") : sanitized;
    }

    private ModelConfigDto.UploadPolicy storedUploadPolicy(LlmProviderModelEntity entity) {
        return JsonUtil.fromJsonQuietly(defaultString(entity.getUploadPolicyJson()), ModelConfigDto.UploadPolicy.class)
                .map(this::sanitizeUploadPolicy)
                .orElseGet(this::disabledUploadPolicy);
    }

    private ModelConfigDto.UploadPolicy disabledUploadPolicy() {
        return new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false);
    }

    private Map<String, Integer> providerSortOrder(List<ModelConfigDto.Provider> defaults) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < defaults.size(); i++) {
            order.put(defaults.get(i).id(), i);
        }
        return order;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private HttpResponse<String> sendProbeRequest(HttpRequest request, String providerName) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalArgumentException(providerName + " 连接失败: " + ex.getMessage(), ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = trim(response.body());
            String extracted = extractErrorMessage(body);
            String detail = extracted.isBlank() ? "" : " - " + extracted;
            throw new IllegalArgumentException(providerName + " 返回状态码 " + response.statusCode() + detail);
        }
        return response;
    }

    private void requestOpenAiModels(String providerName, String baseUrl, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(baseUrl, "/models")))
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .build();
        sendProbeRequest(request, providerName);
    }

    private void requestGeminiModels(String providerName, String baseUrl, String apiKey) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(baseUrl, "/v1beta/models") + "?key=" + encodedKey))
                .GET()
                .header("Accept", "application/json")
                .build();
        sendProbeRequest(request, providerName);
    }

    private void requestAnthropicModels(String providerName, String baseUrl, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(baseUrl, "/v1/models")))
                .GET()
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .build();
        sendProbeRequest(request, providerName);
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String extractErrorMessage(String body) {
        String normalized = trim(body);
        if (normalized.isBlank()) {
            return "";
        }
        try {
            JsonNode root = JsonUtil.fromJson(normalized, JsonNode.class);
            String direct = trim(root.path("message").asText(""));
            if (!direct.isBlank()) {
                return direct;
            }
            direct = trim(root.path("reason").asText(""));
            if (!direct.isBlank()) {
                return direct;
            }
            direct = trim(root.path("detail").asText(""));
            if (!direct.isBlank()) {
                return direct;
            }
            direct = trim(root.path("status").asText(""));
            if (!direct.isBlank()) {
                return direct;
            }
            JsonNode error = root.path("error");
            if (error.isTextual()) {
                return trim(error.asText(""));
            }
            if (error.isObject()) {
                String nested = trim(error.path("message").asText(""));
                if (!nested.isBlank()) {
                    return nested;
                }
                nested = trim(error.path("reason").asText(""));
                if (!nested.isBlank()) {
                    return nested;
                }
                nested = trim(error.path("detail").asText(""));
                if (!nested.isBlank()) {
                    return nested;
                }
                nested = trim(error.path("code").asText(""));
                if (!nested.isBlank()) {
                    return nested;
                }
            }
            return normalized;
        } catch (Exception ex) {
            return normalized;
        }
    }

    private List<String> fetchOllamaModelIds(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalized + "/api/tags"))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to request Ollama: " + ex.getMessage(), ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("Ollama API responded with status " + response.statusCode());
        }
        JsonNode root;
        try {
            root = JsonUtil.fromJson(response.body(), JsonNode.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Ollama response body", ex);
        }
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        JsonNode models = root.path("models");
        if (models.isArray()) {
            for (JsonNode item : models) {
                String name = trim(item.path("name").asText(""));
                if (!name.isBlank()) {
                    modelIds.add(name);
                }
            }
        }
        return List.copyOf(modelIds);
    }

    public record ProbeResult(boolean success, String message) {
    }
}
