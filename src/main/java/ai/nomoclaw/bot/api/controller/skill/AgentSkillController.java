package ai.nomoclaw.bot.api.controller.skill;

import ai.nomoclaw.bot.api.dto.skill.request.UpdateAgentSkillStatusRequest;
import ai.nomoclaw.bot.api.dto.skill.response.AgentSkillResponse;
import ai.nomoclaw.bot.api.mapper.SkillApiMapper;
import ai.nomoclaw.bot.orchestrator.AgentSkillService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent-scoped skill enablement endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class AgentSkillController {

    private final AgentSkillService agentSkillService;

    public AgentSkillController(AgentSkillService agentSkillService) {
        this.agentSkillService = agentSkillService;
    }

    @GetMapping("/agents/{agentUid}/skills")
    public List<AgentSkillResponse> listAgentSkills(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentSkills agentUid={}", agentUid);
        return SkillApiMapper.toAgentSkills(agentSkillService.listAgentSkills(agentUid));
    }

    @PatchMapping("/agents/{agentUid}/skills/{skillKey}")
    public AgentSkillResponse updateAgentSkillStatus(@PathVariable String agentUid,
                                                     @PathVariable String skillKey,
                                                     @Valid @RequestBody UpdateAgentSkillStatusRequest request) {
        log.info("[AgentAPI] updateAgentSkillStatus agentUid={} skillKey={} enabled={}",
                agentUid, skillKey, request.enabled());
        return SkillApiMapper.toAgentSkill(agentSkillService.updateAgentSkillStatus(agentUid, skillKey, request.enabled()));
    }
}
