package ai.nomoclaw.bot.skill;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.skill.model.UpdateSkillBindingsParam;
import ai.nomoclaw.bot.skill.model.GlobalSkillDto;
import ai.nomoclaw.bot.skill.model.SkillBindingAgentDto;
import ai.nomoclaw.bot.skill.model.SkillBindingsDto;
import ai.nomoclaw.bot.skill.model.SkillLinkedAgentDto;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.store.repository.SkillDefinitionRepository;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Skill 全局目录管理服务。
 *
 * <p>负责 skillKey 维度的全局能力：
 * - 全局技能列表与状态维护
 * - 技能与 Agent 绑定矩阵的读取与更新
 * - 技能删除与目录清理
 */
@Service
@Slf4j
public class SkillService {

    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public SkillService(SkillDefinitionRepository skillDefinitionRepository,
                                          AgentSkillRelationRepository agentSkillRelationRepository,
                                          AgentDefinitionRepository agentDefinitionRepository) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    public List<GlobalSkillDto> listSkills() {
        List<SkillDefinitionEntity> definitions = skillDefinitionRepository.listAll();
        return buildGlobalSkills(definitions);
    }

    public GlobalSkillDto updateSkillStatus(String skillKey, boolean enabled) {
        String normalizedSkillKey = normalizeSkillKey(skillKey);
        SkillDefinitionEntity definition = skillDefinitionRepository.findByKey(normalizedSkillKey);
        if (definition == null) {
            throw new IllegalArgumentException("skill not found: " + normalizedSkillKey);
        }
        definition.setStatus(enabled ? "ACTIVE" : "DISABLED");
        definition.setUpdatedTime(LocalDateTime.now());
        skillDefinitionRepository.updateById(definition);
        return buildGlobalSkills(List.of(definition)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("failed to load updated skill: " + normalizedSkillKey));
    }

    public SkillBindingsDto getSkillBindings(String skillKey) {
        SkillDefinitionEntity definition = requireSkill(skillKey);
        return buildSkillBindings(definition);
    }

    @Transactional
    public SkillBindingsDto updateSkillBindings(String skillKey, UpdateSkillBindingsParam command) {
        if (command == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        SkillDefinitionEntity definition = requireSkill(skillKey);
        LocalDateTime now = LocalDateTime.now();
        definition.setStatus(command.enabled() ? "ACTIVE" : "DISABLED");
        definition.setUpdatedTime(now);
        skillDefinitionRepository.updateById(definition);

        Map<String, AgentDefinitionEntity> activeAgentsByUid = agentDefinitionRepository.listAllActive().stream()
                .collect(Collectors.toMap(AgentDefinitionEntity::getAgentUid, agent -> agent, (left, right) -> left, LinkedHashMap::new));
        Map<String, AgentSkillRelationEntity> existingByAgentUid = agentSkillRelationRepository.listBySkillKey(definition.getSkillKey()).stream()
                .collect(Collectors.toMap(AgentSkillRelationEntity::getAgentUid, relation -> relation, (left, right) -> left, LinkedHashMap::new));
        List<UpdateSkillBindingsParam.SkillBindingAgentParam> bindingCommands =
                command.agentBindings() == null ? List.of() : command.agentBindings();
        for (UpdateSkillBindingsParam.SkillBindingAgentParam binding : bindingCommands) {
            if (binding == null) {
                continue;
            }
            String agentUid = binding.agentUid() == null ? "" : binding.agentUid().trim();
            if (agentUid.isBlank() || !activeAgentsByUid.containsKey(agentUid)) {
                continue;
            }
            AgentSkillRelationEntity relation = existingByAgentUid.get(agentUid);
            if (relation == null) {
                relation = new AgentSkillRelationEntity();
                relation.setRelationUid(UuidUtil.newUuid());
                relation.setAgentUid(agentUid);
                relation.setSkillKey(definition.getSkillKey());
                relation.setSortIndex(0);
                relation.setConfigJson("{}");
                relation.setCreatedTime(now);
                relation.setUpdatedTime(now);
                relation.setStatus(binding.enabled() ? "ACTIVE" : "DISABLED");
                agentSkillRelationRepository.save(relation);
                existingByAgentUid.put(agentUid, relation);
                continue;
            }
            relation.setStatus(binding.enabled() ? "ACTIVE" : "DISABLED");
            relation.setUpdatedTime(now);
            agentSkillRelationRepository.updateById(relation);
        }
        return buildSkillBindings(definition);
    }

    public void deleteSkill(String skillKey) {
        String normalizedSkillKey = normalizeSkillKey(skillKey);
        SkillDefinitionEntity definition = skillDefinitionRepository.findByKey(normalizedSkillKey);
        if (definition == null) {
            throw new IllegalArgumentException("skill not found: " + normalizedSkillKey);
        }
        agentSkillRelationRepository.deleteBySkillKey(normalizedSkillKey);
        skillDefinitionRepository.deleteBySkillKey(normalizedSkillKey);
        deleteDirectoryRecursively(resolveSkillDirectory(definition));
        log.info("[SkillService] deleted skillKey={} path={}", normalizedSkillKey, definition.getSkillPath());
    }

    private SkillBindingsDto buildSkillBindings(SkillDefinitionEntity definition) {
        List<AgentDefinitionEntity> agents = agentDefinitionRepository.listAllActive();
        Map<String, AgentSkillRelationEntity> relationsByAgentUid = agentSkillRelationRepository.listBySkillKey(definition.getSkillKey()).stream()
                .collect(Collectors.toMap(AgentSkillRelationEntity::getAgentUid, relation -> relation, (left, right) -> left, LinkedHashMap::new));
        List<SkillBindingAgentDto> bindings = agents.stream()
                .map(agent -> {
                    AgentSkillRelationEntity relation = relationsByAgentUid.get(agent.getAgentUid());
                    boolean enabled = relation != null && "ACTIVE".equalsIgnoreCase(relation.getStatus());
                    return new SkillBindingAgentDto(
                            agent.getAgentUid(),
                            agent.getAgentName(),
                            agent.getDisplayName(),
                            enabled
                    );
                })
                .toList();
        int enabledAgentCount = (int) bindings.stream().filter(SkillBindingAgentDto::enabled).count();
        return new SkillBindingsDto(
                definition.getSkillKey(),
                definition.getDisplayName(),
                definition.getDescription(),
                definition.getSkillPath(),
                definition.getStatus(),
                definition.getUpdatedTime(),
                enabledAgentCount,
                bindings
        );
    }

    private List<GlobalSkillDto> buildGlobalSkills(List<SkillDefinitionEntity> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<String> skillKeys = definitions.stream()
                .map(SkillDefinitionEntity::getSkillKey)
                .filter(key -> key != null && !key.isBlank())
                .toList();
        Map<String, List<AgentSkillRelationEntity>> relationsBySkillKey = agentSkillRelationRepository.listActiveBySkillKeys(skillKeys)
                .stream()
                .collect(Collectors.groupingBy(AgentSkillRelationEntity::getSkillKey, LinkedHashMap::new, Collectors.toList()));
        Collection<String> agentUids = relationsBySkillKey.values().stream()
                .flatMap(List::stream)
                .map(AgentSkillRelationEntity::getAgentUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, AgentDefinitionEntity> agentsByUid = agentDefinitionRepository.listActiveByUids(agentUids).stream()
                .collect(Collectors.toMap(AgentDefinitionEntity::getAgentUid, agent -> agent, (left, right) -> left, LinkedHashMap::new));
        return definitions.stream()
                .map(definition -> new GlobalSkillDto(
                        definition.getSkillKey(),
                        definition.getDisplayName(),
                        definition.getDescription(),
                        definition.getSkillPath(),
                        definition.getStatus(),
                        definition.getUpdatedTime(),
                        relationsBySkillKey.getOrDefault(definition.getSkillKey(), List.of()).stream()
                                .map(relation -> toLinkedAgent(agentsByUid.get(relation.getAgentUid())))
                                .filter(Objects::nonNull)
                                .toList()
                ))
                .toList();
    }

    private SkillLinkedAgentDto toLinkedAgent(AgentDefinitionEntity agent) {
        if (agent == null) {
            return null;
        }
        return new SkillLinkedAgentDto(agent.getAgentUid(), agent.getAgentName(), agent.getDisplayName());
    }

    private String normalizeSkillKey(String skillKey) {
        return skillKey == null ? "" : skillKey.trim();
    }

    private SkillDefinitionEntity requireSkill(String skillKey) {
        String normalizedSkillKey = normalizeSkillKey(skillKey);
        if (normalizedSkillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must not be blank");
        }
        SkillDefinitionEntity definition = skillDefinitionRepository.findByKey(normalizedSkillKey);
        if (definition == null) {
            throw new IllegalArgumentException("skill not found: " + normalizedSkillKey);
        }
        return definition;
    }

    private Path resolveSkillDirectory(SkillDefinitionEntity definition) {
        String configuredPath = definition.getSkillPath() == null ? "" : definition.getSkillPath().trim();
        if (configuredPath.isBlank()) {
            return NomoClawPaths.skillsRoot().resolve(definition.getSkillKey()).toAbsolutePath().normalize();
        }
        Path path = Path.of(configuredPath);
        if (!path.isAbsolute()) {
            path = NomoClawPaths.skillsRoot().resolve(configuredPath);
        }
        return path.toAbsolutePath().normalize();
    }

    private void deleteDirectoryRecursively(Path rootPath) {
        if (rootPath == null || !Files.exists(rootPath)) {
            return;
        }
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete skill directory: " + rootPath, ex);
        }
    }
}
