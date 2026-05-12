package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.dto.AgentSkillDto;
import ai.nomoclaw.bot.store.entity.AgentSkillRelationEntity;
import ai.nomoclaw.bot.store.entity.SkillDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.store.repository.SkillDefinitionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Skill relation service for agent-scoped enable/disable and listing.
 *
 * <p>This service intentionally bypasses AgentApplicationService forwarding.
 */
@Service
public class AgentSkillService {

    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;

    public AgentSkillService(SkillDefinitionRepository skillDefinitionRepository,
                             AgentSkillRelationRepository agentSkillRelationRepository) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
    }

    /**
     * Lists all active skills and resolves whether each skill is enabled for the target agent.
     */
    public List<AgentSkillDto> listAgentSkills(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        List<SkillDefinitionEntity> skillDefinitions = skillDefinitionRepository.listAllActive();
        Map<String, AgentSkillRelationEntity> relationsBySkillKey = agentSkillRelationRepository.listByAgentUid(normalizedAgentUid)
                .stream()
                .collect(Collectors.toMap(
                        AgentSkillRelationEntity::getSkillKey,
                        relation -> relation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return skillDefinitions.stream()
                .map(skill -> {
                    AgentSkillRelationEntity relation = relationsBySkillKey.get(skill.getSkillKey());
                    boolean enabled = relation != null && "ACTIVE".equalsIgnoreCase(relation.getStatus());
                    LocalDateTime updatedTime = relation == null ? skill.getUpdatedTime() : relation.getUpdatedTime();
                    return new AgentSkillDto(
                            skill.getSkillKey(),
                            skill.getDisplayName(),
                            skill.getDescription(),
                            skill.getSkillPath(),
                            enabled,
                            updatedTime
                    );
                })
                .toList();
    }

    /**
     * Updates the enable status of one skill for the target agent and returns the current skill view.
     */
    public AgentSkillDto updateAgentSkillStatus(String agentUid, String skillKey, boolean enabled) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        String normalizedSkillKey = skillKey == null ? "" : skillKey.trim();
        if (normalizedSkillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must not be blank");
        }
        SkillDefinitionEntity skill = skillDefinitionRepository.findActiveByKey(normalizedSkillKey);
        if (skill == null) {
            throw new IllegalArgumentException("skill not found: " + normalizedSkillKey);
        }

        AgentSkillRelationEntity relation = agentSkillRelationRepository.findByAgentUidAndSkillKey(normalizedAgentUid, normalizedSkillKey);
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = enabled ? "ACTIVE" : "DISABLED";
        if (relation == null) {
            relation = new AgentSkillRelationEntity();
            relation.setRelationUid(UUID.randomUUID().toString());
            relation.setAgentUid(normalizedAgentUid);
            relation.setSkillKey(normalizedSkillKey);
            relation.setStatus(nextStatus);
            relation.setSortIndex(0);
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
            relation.setUpdatedTime(now);
            agentSkillRelationRepository.save(relation);
        } else {
            relation.setStatus(nextStatus);
            relation.setUpdatedTime(now);
            agentSkillRelationRepository.updateById(relation);
        }

        return new AgentSkillDto(
                skill.getSkillKey(),
                skill.getDisplayName(),
                skill.getDescription(),
                skill.getSkillPath(),
                enabled,
                relation.getUpdatedTime()
        );
    }

    /**
     * Applies a stable default agent when caller input is blank.
     */
    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }
}
