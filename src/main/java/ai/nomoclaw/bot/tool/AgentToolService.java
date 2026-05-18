package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.store.entity.AgentToolRelationEntity;
import ai.nomoclaw.bot.store.entity.ToolDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentToolRelationRepository;
import ai.nomoclaw.bot.store.repository.ToolDefinitionRepository;
import ai.nomoclaw.bot.tool.model.AgentToolDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentToolService {

    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    private final AgentToolRelationRepository agentToolRelationRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;

    public AgentToolService(AgentToolRelationRepository agentToolRelationRepository,
                            ToolDefinitionRepository toolDefinitionRepository) {
        this.agentToolRelationRepository = agentToolRelationRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
    }

    public List<AgentToolDto> listAgentTools(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        List<ToolDefinitionEntity> toolDefinitions = toolDefinitionRepository.listAllActive();
        Map<String, AgentToolRelationEntity> relationsByToolKey = agentToolRelationRepository.listByAgentUid(normalizedAgentUid)
                .stream()
                .collect(Collectors.toMap(
                        AgentToolRelationEntity::getToolKey,
                        relation -> relation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return toolDefinitions.stream()
                .map(tool -> {
                    AgentToolRelationEntity relation = relationsByToolKey.get(tool.getToolKey());
                    boolean enabled = relation != null && "ACTIVE".equalsIgnoreCase(relation.getStatus());
                    LocalDateTime updatedTime = relation == null ? tool.getUpdatedTime() : relation.getUpdatedTime();
                    return new AgentToolDto(
                            tool.getToolKey(),
                            tool.getDisplayName(),
                            tool.getDescription(),
                            enabled,
                            updatedTime
                    );
                })
                .toList();
    }

    public AgentToolDto updateAgentToolStatus(String agentUid, String toolKey, boolean enabled) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        String normalizedToolKey = toolKey == null ? "" : toolKey.trim();
        if (normalizedToolKey.isBlank()) {
            throw new IllegalArgumentException("toolKey must not be blank");
        }
        ToolDefinitionEntity tool = toolDefinitionRepository.findActiveByKey(normalizedToolKey);
        if (tool == null) {
            throw new IllegalArgumentException("tool not found: " + normalizedToolKey);
        }

        AgentToolRelationEntity relation = agentToolRelationRepository.findByAgentUidAndToolKey(normalizedAgentUid, normalizedToolKey);
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = enabled ? "ACTIVE" : "DISABLED";
        if (relation == null) {
            relation = new AgentToolRelationEntity();
            relation.setRelationUid(UUID.randomUUID().toString());
            relation.setAgentUid(normalizedAgentUid);
            relation.setToolKey(normalizedToolKey);
            relation.setStatus(nextStatus);
            relation.setSortIndex(0);
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
            relation.setUpdatedTime(now);
            agentToolRelationRepository.save(relation);
        } else {
            relation.setStatus(nextStatus);
            relation.setUpdatedTime(now);
            agentToolRelationRepository.updateById(relation);
        }

        return new AgentToolDto(
                tool.getToolKey(),
                tool.getDisplayName(),
                tool.getDescription(),
                enabled,
                relation.getUpdatedTime()
        );
    }

    public void initializeAgentToolRelations(String agentUid, LocalDateTime now) {
        List<ToolDefinitionEntity> tools = toolDefinitionRepository.listAllActive();
        for (ToolDefinitionEntity tool : tools) {
            AgentToolRelationEntity relation = new AgentToolRelationEntity();
            relation.setRelationUid(UUID.randomUUID().toString());
            relation.setAgentUid(agentUid);
            relation.setToolKey(tool.getToolKey());
            relation.setStatus("ACTIVE");
            relation.setSortIndex(tool.getSortIndex() == null ? 0 : tool.getSortIndex());
            relation.setConfigJson("{}");
            relation.setCreatedTime(now);
            relation.setUpdatedTime(now);
            agentToolRelationRepository.save(relation);
        }
    }

    public void purgeAgentTools(String agentUid) {
        agentToolRelationRepository.deleteByAgentUid(normalizeAgentUid(agentUid));
    }

    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }
}
