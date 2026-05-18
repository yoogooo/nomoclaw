package ai.nomoclaw.bot.skill;

import ai.nomoclaw.bot.skill.model.CreateSkillParam;
import ai.nomoclaw.bot.skill.model.ImportSkillFromUrlParam;
import ai.nomoclaw.bot.skill.model.AgentSkillDto;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.store.repository.SkillDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 导入与创建服务。
 *
 * <p>负责将外部来源（URL、压缩包）或本地创建请求转化为可用技能，
 * 并完成文件落盘、元数据入库及可选的 Agent 绑定初始化。
 */
@Service
public class SkillImportService {

    private static final String SKILL_FILE = "SKILL.md";
    private static final Set<String> URL_IMPORT_HOSTS = Set.of(
            "skills.sh",
            "clawhub.ai",
            "skillsmp.com",
            "lobehub.com",
            "market.lobehub.com",
            "github.com",
            "modelscope.cn"
    );
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final String ARCHIVE_STRUCTURE_INVALID_MESSAGE =
            "archive skill structure invalid: SKILL.md must be at archive root or single top-level directory root";

    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final HttpClient httpClient;

    public SkillImportService(SkillDefinitionRepository skillDefinitionRepository,
                                         AgentSkillRelationRepository agentSkillRelationRepository,
                                         AgentDefinitionRepository agentDefinitionRepository,
                                         HttpClient appHttpClient) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.httpClient = appHttpClient;
    }

    public AgentSkillDto importSkillFromUrl(String agentUid, ImportSkillFromUrlParam command) {
        if (command == null || command.url() == null || command.url().isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        String normalizedAgentUid = requireAgent(agentUid).getAgentUid();
        try {
            ResolvedImport resolved = resolveUrlImport(URI.create(command.url().trim()), 0);
            ImportedSkill imported = importArchiveInternal(normalizedAgentUid, resolved.archivePath(), command.attachToAgent(), resolved.archiveSubPath());
            return imported.dto();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to import skill from url: " + ex.getMessage(), ex);
        }
    }

    public AgentSkillDto importSkillArchive(String agentUid, MultipartFile file, boolean attachToAgent) {
        requireAgent(agentUid);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("archive file must not be empty");
        }
        String originalName = file.getOriginalFilename() == null ? "skill-import.zip" : file.getOriginalFilename().trim();
        try {
            Path tempArchive = Files.createTempFile("skill-import-", "-" + sanitizeFileName(originalName));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempArchive, StandardCopyOption.REPLACE_EXISTING);
            }
            ImportedSkill imported = importArchiveInternal(agentUid, tempArchive, attachToAgent, null);
            return imported.dto();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to import skill archive: " + ex.getMessage(), ex);
        }
    }

    public AgentSkillDto createSkill(String agentUid, CreateSkillParam command) {
        AgentDefinitionEntity agent = requireAgent(agentUid);
        if (command == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String skillKey = normalizeSkillKey(command.skillKey());
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must not be blank");
        }
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (command.description() == null || command.description().isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (skillDefinitionRepository.findByKey(skillKey) != null) {
            throw new IllegalArgumentException("skill already exists: " + skillKey);
        }

        Path skillsRoot = ensureSkillsRoot();
        Path targetDir = skillsRoot.resolve(skillKey).toAbsolutePath().normalize();
        if (Files.exists(targetDir)) {
            throw new IllegalArgumentException("skill path already exists: " + targetDir);
        }

        try {
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve(SKILL_FILE), buildSkillMarkdown(
                    command.displayName().trim(),
                    command.description().trim(),
                    command.purpose() == null ? "" : command.purpose().trim()
            ));
            ImportedSkill imported = registerSkill(agent.getAgentUid(), skillKey, command.displayName().trim(), command.description().trim(), targetDir, command.attachToAgent());
            return imported.dto();
        } catch (Exception ex) {
            deleteQuietly(targetDir);
            throw new IllegalArgumentException("failed to create skill: " + ex.getMessage(), ex);
        }
    }

    private ImportedSkill importArchiveInternal(String agentUid, Path archivePath, boolean attachToAgent, String archiveSubPath) throws Exception {
        Path tempDir = Files.createTempDirectory("skill-import-archive-");
        try {
            Path extractedDir = tempDir.resolve("extracted");
            Files.createDirectories(extractedDir);
            extractArchive(archivePath, extractedDir);
            Path skillRoot = locateSkillRoot(extractedDir, archiveSubPath);
            return importFromSkillDir(agentUid, skillRoot, attachToAgent, null);
        } finally {
            deleteQuietly(tempDir);
            deleteQuietly(archivePath);
        }
    }

    private ImportedSkill importFromSkillDir(String agentUid, Path sourceSkillDir, boolean attachToAgent, String forcedSkillKey) throws Exception {
        SkillMetadata metadata = loadSkillMetadata(sourceSkillDir);
        if (metadata == null) {
            throw new IllegalArgumentException("SKILL.md missing or metadata is incomplete");
        }

        String skillKey = forcedSkillKey == null || forcedSkillKey.isBlank()
                ? normalizeSkillKey(metadata.name())
                : normalizeSkillKey(forcedSkillKey);
        if (skillKey.isBlank()) {
            skillKey = normalizeSkillKey(sourceSkillDir.getFileName() == null ? "skill" : sourceSkillDir.getFileName().toString());
        }
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("unable to determine skillKey");
        }
        if (skillDefinitionRepository.findByKey(skillKey) != null) {
            throw new IllegalArgumentException("skill already exists: " + skillKey);
        }

        Path targetDir = ensureSkillsRoot().resolve(skillKey).toAbsolutePath().normalize();
        if (Files.exists(targetDir)) {
            throw new IllegalArgumentException("skill path already exists: " + targetDir);
        }
        copyDirectory(sourceSkillDir, targetDir);
        return registerSkill(agentUid, skillKey, metadata.name(), metadata.description(), targetDir, attachToAgent);
    }

    private ImportedSkill registerSkill(String agentUid,
                                        String skillKey,
                                        String displayName,
                                        String description,
                                        Path skillDir,
                                        boolean attachToAgent) {
        LocalDateTime now = LocalDateTime.now();
        SkillDefinitionEntity definition = new SkillDefinitionEntity();
        definition.setSkillKey(skillKey);
        definition.setDisplayName(displayName);
        definition.setDescription(description);
        definition.setSkillPath(skillDir.toAbsolutePath().normalize().toString());
        definition.setStatus("ACTIVE");
        definition.setSortIndex(0);
        definition.setConfigJson("{}");
        definition.setCreatedTime(now);
        definition.setUpdatedTime(now);
        skillDefinitionRepository.save(definition);

        boolean enabled = false;
        if (attachToAgent) {
            enabled = true;
            upsertAgentSkillRelation(agentUid, skillKey, now);
        }

        return new ImportedSkill(new AgentSkillDto(
                skillKey,
                displayName,
                description,
                skillDir.toAbsolutePath().normalize().toString(),
                enabled,
                now
        ));
    }

    private void upsertAgentSkillRelation(String agentUid, String skillKey, LocalDateTime now) {
        AgentSkillRelationEntity relation = agentSkillRelationRepository.findByAgentUidAndSkillKey(agentUid, skillKey);
        if (relation == null) {
            relation = new AgentSkillRelationEntity();
            relation.setRelationUid(UUID.randomUUID().toString());
            relation.setAgentUid(agentUid);
            relation.setSkillKey(skillKey);
            relation.setStatus("ACTIVE");
            relation.setSortIndex(0);
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
            relation.setUpdatedTime(now);
            agentSkillRelationRepository.save(relation);
            return;
        }
        relation.setStatus("ACTIVE");
        relation.setUpdatedTime(now);
        agentSkillRelationRepository.updateById(relation);
    }

    private AgentDefinitionEntity requireAgent(String agentUid) {
        String normalized = agentUid == null ? "" : agentUid.trim();
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalized);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + normalized);
        }
        return agent;
    }

    private Path ensureSkillsRoot() {
        try {
            Path root = NomoClawPaths.skillsRoot();
            Files.createDirectories(root);
            return root;
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to initialize skills root", ex);
        }
    }

    private ResolvedImport resolveUrlImport(URI uri, int depth) throws Exception {
        if (depth > 3) {
            throw new IllegalArgumentException("too many url redirects while resolving skill");
        }
        String host = normalizeHost(uri.getHost());
        if (!isAllowedImportHost(host)) {
            throw new IllegalArgumentException("unsupported skill url source: " + host);
        }
        if ("github.com".equals(host)) {
            return resolveGitHubImport(uri);
        }
        if (looksLikeArchivePath(uri.getPath())) {
            return new ResolvedImport(downloadArchive(uri), null, null);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "NomoClawSkillImporter/1.0")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        URI finalUri = response.uri();
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (isArchiveContentType(contentType) || looksLikeArchivePath(finalUri.getPath())) {
            Path archive = writeTempBytes(response.body(), guessArchiveFileName(finalUri.getPath(), contentType));
            return new ResolvedImport(archive, null, null);
        }

        if (!Objects.equals(finalUri, uri)) {
            String finalHost = normalizeHost(finalUri.getHost());
            if ("github.com".equals(finalHost)) {
                return resolveGitHubImport(finalUri);
            }
        }

        String html = new String(response.body(), StandardCharsets.UTF_8);
        for (URI candidate : extractCandidateUrls(finalUri, html)) {
            String candidateHost = normalizeHost(candidate.getHost());
            if (!candidateHost.isBlank() && isAllowedImportHost(candidateHost)) {
                try {
                    return resolveUrlImport(candidate, depth + 1);
                } catch (IllegalArgumentException ignored) {
                    // continue trying next candidate
                }
            } else if (!candidateHost.isBlank() && isAllowedImportHost(candidateHost) && looksLikeArchivePath(candidate.getPath())) {
                return new ResolvedImport(downloadArchive(candidate), null, null);
            }
        }
        throw new IllegalArgumentException("unable to locate downloadable skill package from url");
    }

    private ResolvedImport resolveGitHubImport(URI uri) throws Exception {
        List<String> segments = pathSegments(uri.getPath());
        if (segments.size() < 2) {
            throw new IllegalArgumentException("unsupported github url: " + uri);
        }
        String owner = segments.get(0);
        String repo = segments.get(1);
        String branch = "main";
        String subPath = null;
        if (segments.size() >= 5 && "tree".equals(segments.get(2))) {
            branch = segments.get(3);
            subPath = String.join("/", segments.subList(4, segments.size()));
        } else if (segments.size() >= 2 && segments.size() < 5) {
            subPath = null;
        }
        URI zipUri = URI.create("https://codeload.github.com/" + owner + "/" + repo + "/zip/refs/heads/" + branch);
        String archiveSubPath = subPath == null || subPath.isBlank() ? null : repo + "-" + branch + "/" + subPath;
        String suggestedKey = subPath == null || subPath.isBlank()
                ? normalizeSkillKey(repo)
                : normalizeSkillKey(segments.get(segments.size() - 1));
        return new ResolvedImport(downloadArchive(zipUri), archiveSubPath, suggestedKey);
    }

    private Path downloadArchive(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "NomoClawSkillImporter/1.0")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("failed to download skill archive: HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        return writeTempBytes(response.body(), guessArchiveFileName(response.uri().getPath(), contentType));
    }

    private Path writeTempBytes(byte[] bytes, String fileName) throws Exception {
        Path path = Files.createTempFile("skill-import-", "-" + sanitizeFileName(fileName));
        Files.write(path, bytes);
        return path;
    }

    private void extractArchive(Path archivePath, Path outputDir) throws Exception {
        String lowerName = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip")) {
            unzipArchive(archivePath, outputDir);
            return;
        }
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) {
            extractTarGz(archivePath, outputDir);
            return;
        }
        throw new IllegalArgumentException("unsupported archive type: " + archivePath.getFileName());
    }

    private void unzipArchive(Path archivePath, Path outputDir) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archivePath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path resolved = outputDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(outputDir)) {
                    throw new IllegalArgumentException("archive contains illegal path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zipInputStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private void extractTarGz(Path archivePath, Path outputDir) throws Exception {
        Process process = new ProcessBuilder("tar", "-xzf", archivePath.toString(), "-C", outputDir.toString()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalArgumentException("failed to extract tar.gz archive: " + error.trim());
        }
    }

    private Path locateSkillRoot(Path extractedDir, String preferredSubPath) throws Exception {
        if (preferredSubPath != null && !preferredSubPath.isBlank()) {
            Path preferred = extractedDir.resolve(preferredSubPath).normalize();
            if (Files.isDirectory(preferred) && Files.isRegularFile(preferred.resolve(SKILL_FILE))) {
                return preferred;
            }
        }
        if (Files.isRegularFile(extractedDir.resolve(SKILL_FILE))) {
            return extractedDir;
        }

        List<Path> topLevelEntries;
        try (var stream = Files.list(extractedDir)) {
            topLevelEntries = stream
                    .filter(path -> {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        return !"__MACOSX".equals(name) && !".DS_Store".equals(name);
                    })
                    .toList();
        }
        if (topLevelEntries.size() != 1 || !Files.isDirectory(topLevelEntries.get(0))) {
            throw new IllegalArgumentException(ARCHIVE_STRUCTURE_INVALID_MESSAGE);
        }

        Path singleTopLevelDir = topLevelEntries.get(0);
        if (Files.isRegularFile(singleTopLevelDir.resolve(SKILL_FILE))) {
            return singleTopLevelDir;
        }
        throw new IllegalArgumentException(ARCHIVE_STRUCTURE_INVALID_MESSAGE);
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws Exception {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                Path relative = sourceDir.relativize(dir);
                Files.createDirectories(targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Path relative = sourceDir.relativize(file);
                Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private SkillMetadata loadSkillMetadata(Path skillDir) throws Exception {
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            return null;
        }
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        FrontMatter frontMatter = parseFrontMatter(content);
        if (frontMatter.name() == null || frontMatter.name().isBlank()
                || frontMatter.description() == null || frontMatter.description().isBlank()) {
            return null;
        }
        return new SkillMetadata(frontMatter.name().trim(), frontMatter.description().trim());
    }

    private FrontMatter parseFrontMatter(String markdown) throws Exception {
        if (markdown == null) {
            return new FrontMatter(null, null);
        }
        String normalized = markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new FrontMatter(null, null);
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            return new FrontMatter(null, null);
        }

        String frontMatter = normalized.substring(4, end);
        FrontMatterParsingState state = new FrontMatterParsingState();
        List<String> descriptionLines = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(frontMatter.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)) {
            StringBuilder lineBuffer = new StringBuilder();
            int read;
            while ((read = reader.read()) >= 0) {
                if (read == '\n') {
                    String line = lineBuffer.toString().stripTrailing();
                    lineBuffer.setLength(0);
                    applyFrontMatterLine(state, descriptionLines, line);
                } else {
                    lineBuffer.append((char) read);
                }
            }
            if (!lineBuffer.isEmpty()) {
                applyFrontMatterLine(state, descriptionLines, lineBuffer.toString().stripTrailing());
            }
        }
        if (!descriptionLines.isEmpty()) {
            state.description = String.join("\n", descriptionLines).trim();
        }
        return new FrontMatter(state.name, state.description);
    }

    private void applyFrontMatterLine(FrontMatterParsingState state, List<String> descriptionLines, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (line.startsWith(" ") || line.startsWith("\t")) {
            if (state.collectingDescription) {
                descriptionLines.add(trimmed);
            }
            return;
        }
        state.collectingDescription = false;
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0) {
            return;
        }
        String key = line.substring(0, colonIndex).trim();
        String value = stripQuotes(line.substring(colonIndex + 1).trim());
        if ("name".equals(key)) {
            state.name = value;
            return;
        }
        if ("description".equals(key)) {
            if ("|".equals(value) || ">".equals(value)) {
                state.collectingDescription = true;
                return;
            }
            state.description = value;
        }
    }

    private List<URI> extractCandidateUrls(URI baseUri, String html) {
        LinkedHashSet<URI> candidates = new LinkedHashSet<>();
        Matcher hrefMatcher = HREF_PATTERN.matcher(html);
        while (hrefMatcher.find()) {
            addCandidate(candidates, baseUri, hrefMatcher.group(1));
        }
        Matcher urlMatcher = URL_PATTERN.matcher(html);
        while (urlMatcher.find()) {
            addCandidate(candidates, baseUri, urlMatcher.group());
        }
        return List.copyOf(candidates);
    }

    private void addCandidate(Set<URI> target, URI baseUri, String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("#") || raw.startsWith("javascript:")) {
            return;
        }
        try {
            URI candidate = baseUri.resolve(raw).normalize();
            String candidateText = candidate.toString();
            if (!candidateText.startsWith("http://") && !candidateText.startsWith("https://")) {
                return;
            }
            target.add(candidate);
        } catch (Exception ignored) {
            // skip malformed candidates
        }
    }

    private boolean isAllowedImportHost(String host) {
        return URL_IMPORT_HOSTS.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    private boolean isArchiveContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("zip")
                || normalized.contains("gzip")
                || normalized.contains("x-gtar")
                || normalized.contains("octet-stream");
    }

    private boolean looksLikeArchivePath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".zip")
                || normalized.endsWith(".tar.gz")
                || normalized.endsWith(".tgz")
                || normalized.contains("/download");
    }

    private String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> pathSegments(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                segments.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
            }
        }
        return segments;
    }

    private String normalizeSkillKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized;
    }

    private String buildSkillMarkdown(String displayName, String description, String purpose) {
        StringBuilder builder = new StringBuilder();
        builder.append("---\n");
        builder.append("name: ").append(displayName).append('\n');
        builder.append("description: ").append(description).append('\n');
        builder.append("---\n\n");
        builder.append("# ").append(displayName).append("\n\n");
        builder.append("## Purpose\n");
        builder.append(purpose.isBlank() ? "Describe when this skill should be used and what outcome it should produce." : purpose).append("\n\n");
        builder.append("## How To Use\n");
        builder.append("- State when this skill should be selected.\n");
        builder.append("- Explain required inputs or constraints.\n");
        builder.append("- Keep instructions concrete and reusable.\n");
        return builder.toString();
    }

    private String guessArchiveFileName(String path, String contentType) {
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalizedPath.endsWith(".tar.gz")) return "skill.tar.gz";
        if (normalizedPath.endsWith(".tgz")) return "skill.tgz";
        if (normalizedPath.endsWith(".zip")) return "skill.zip";
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("gzip")) return "skill.tar.gz";
        return "skill.zip";
    }

    private String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.isBlank() ? "skill.zip" : sanitized;
    }

    private String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc) throws java.io.IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }

    private record ImportedSkill(AgentSkillDto dto) {
    }

    private record ResolvedImport(Path archivePath, String archiveSubPath, String suggestedSkillKey) {
    }

    private record SkillMetadata(String name, String description) {
    }

    private record FrontMatter(String name, String description) {
    }

    private static class FrontMatterParsingState {
        private String name;
        private String description;
        private boolean collectingDescription;
    }
}
