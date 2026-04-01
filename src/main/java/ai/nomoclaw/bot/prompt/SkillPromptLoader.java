package ai.nomoclaw.bot.prompt;

import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.store.repository.SkillDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class SkillPromptLoader {

    private static final String SKILLS_DIR = "skills";
    private static final String SKILL_FILE = "SKILL.md";

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;

    public SkillPromptLoader(AgentDefinitionRepository agentDefinitionRepository,
                             SkillDefinitionRepository skillDefinitionRepository,
                             AgentSkillRelationRepository agentSkillRelationRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
    }

    public String buildAgentSkillPrompt(PromptLoader.PromptContext context) {
        Path workingDirectory = context.workingDirectory();
        Path skillsRoot = resolveSkillsRoot(workingDirectory);
        if (!Files.isDirectory(skillsRoot)) {
            return "";
        }

        List<SkillMetadata> skills = resolveSkills(context.agentName(), skillsRoot);
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Agent Skills\n");
        builder.append("以下为当前 agent 可用的 skills。");
        builder.append("这部分只提供技能索引，不会自动展开各 skill 的完整内容。\n");
        builder.append("当任务明显匹配某个 skill 时，先读取对应的 SKILL.md；");
        builder.append("如有需要，再继续读取同目录下的模板、示例或脚本。\n\n");
        for (SkillMetadata skill : skills) {
            builder.append("## ").append(skill.name()).append('\n');
            builder.append(skill.description()).append('\n');
            builder.append("Check \"")
                    .append(skill.skillFile().toAbsolutePath().normalize())
                    .append("\" for how to use this skill\n\n");
        }
        return builder.toString().trim();
    }

    static Path resolveSkillsRoot(Path workingDirectory) {
        Path base = workingDirectory == null
                ? NomoClawPaths.root()
                : workingDirectory;
        return base.toAbsolutePath().normalize().resolve(SKILLS_DIR);
    }

    static List<SkillMetadata> listAvailableSkills(Path skillsRoot) {
        try (var children = Files.list(skillsRoot)) {
            return children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(SkillPromptLoader::loadSkillMetadata)
                    .filter(skill -> skill != null)
                    .toList();
        } catch (IOException e) {
            log.warn("[SkillPromptLoader] failed to scan skills root={} err={}", skillsRoot, e.toString());
            return List.of();
        }
    }

    private List<SkillMetadata> resolveSkills(String agentName, Path skillsRoot) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findActiveByName(agentName);
        if (agent == null) {
            return listAvailableSkills(skillsRoot);
        }

        List<AgentSkillRelationEntity> relations = agentSkillRelationRepository.listActiveByAgentUid(agent.getAgentUid());
        if (relations.isEmpty()) {
            return listAvailableSkills(skillsRoot);
        }

        Map<String, SkillDefinitionEntity> definitionsByKey = skillDefinitionRepository.listActiveByKeys(
                        relations.stream().map(AgentSkillRelationEntity::getSkillKey).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        SkillDefinitionEntity::getSkillKey,
                        definition -> definition,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<SkillMetadata> skills = new ArrayList<>();
        for (AgentSkillRelationEntity relation : relations) {
            SkillDefinitionEntity definition = definitionsByKey.get(relation.getSkillKey());
            if (definition == null) {
                continue;
            }
            SkillMetadata metadata = loadConfiguredSkill(skillsRoot, definition);
            if (metadata != null) {
                skills.add(metadata);
            }
        }
        return skills;
    }

    private SkillMetadata loadConfiguredSkill(Path skillsRoot, SkillDefinitionEntity definition) {
        String configuredPath = definition.getSkillPath() == null ? "" : definition.getSkillPath().trim();
        Path skillDir = configuredPath.isBlank()
                ? skillsRoot.resolve(definition.getSkillKey())
                : resolveConfiguredSkillDir(skillsRoot, configuredPath);
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            log.warn("[SkillPromptLoader] skip missing skill file skillKey={} path={}", definition.getSkillKey(), skillFile);
            return null;
        }

        SkillMetadata metadata = loadSkillMetadata(skillDir);
        if (metadata == null) {
            String displayName = definition.getDisplayName();
            String description = definition.getDescription();
            if (displayName == null || displayName.isBlank() || description == null || description.isBlank()) {
                return null;
            }
            return new SkillMetadata(
                    displayName.trim(),
                    description.trim(),
                    skillDir.toAbsolutePath().normalize(),
                    skillFile.toAbsolutePath().normalize()
            );
        }
        return metadata;
    }

    private static Path resolveConfiguredSkillDir(Path skillsRoot, String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return skillsRoot.getParent().resolve(path).toAbsolutePath().normalize();
    }

    private static SkillMetadata loadSkillMetadata(Path skillDir) {
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            return null;
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            FrontMatter frontMatter = parseFrontMatter(content);
            String name = frontMatter.name();
            String description = frontMatter.description();
            if (name == null || name.isBlank() || description == null || description.isBlank()) {
                log.warn("[SkillPromptLoader] skip skill missing metadata dir={}", skillDir);
                return null;
            }
            return new SkillMetadata(name.trim(), description.trim(), skillDir.toAbsolutePath().normalize(), skillFile.toAbsolutePath().normalize());
        } catch (Exception e) {
            log.warn("[SkillPromptLoader] failed to load skill dir={} err={}", skillDir, e.toString());
            return null;
        }
    }

    private static FrontMatter parseFrontMatter(String markdown) {
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
        String name = null;
        String description = null;
        List<String> descriptionLines = new ArrayList<>();
        boolean collectingDescription = false;

        for (String rawLine : frontMatter.split("\n")) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (collectingDescription) {
                    descriptionLines.add(trimmed);
                }
                continue;
            }

            collectingDescription = false;
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            String key = line.substring(0, colonIndex).trim();
            String value = stripQuotes(line.substring(colonIndex + 1).trim());
            if ("name".equals(key)) {
                name = value;
            } else if ("description".equals(key)) {
                if (value.equals("|") || value.equals(">")) {
                    collectingDescription = true;
                } else {
                    description = value;
                }
            }
        }

        if (!descriptionLines.isEmpty()) {
            description = String.join("\n", descriptionLines).trim();
        }

        return new FrontMatter(name, description);
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    record SkillMetadata(String name, String description, Path skillDir, Path skillFile) {
    }

    private record FrontMatter(String name, String description) {
    }
}
