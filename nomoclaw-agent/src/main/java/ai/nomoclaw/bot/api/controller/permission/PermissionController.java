package ai.nomoclaw.bot.api.controller.permission;

import ai.nomoclaw.bot.api.dto.permission.request.UpdatePermissionRulesRequest;
import ai.nomoclaw.bot.api.dto.permission.response.PermissionRulesResponse;
import ai.nomoclaw.bot.api.mapper.PermissionApiMapper;
import ai.nomoclaw.bot.orchestrator.PermissionAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission rule query and mutation endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class PermissionController {

    private final PermissionAppService permissionAppService;

    public PermissionController(PermissionAppService permissionAppService) {
        this.permissionAppService = permissionAppService;
    }

    @GetMapping("/permissions/effective")
    public PermissionRulesResponse getEffectivePermissions(@RequestParam(required = false, defaultValue = "") String conversationUid,
                                                           @RequestParam(required = false, defaultValue = "") String agentUid) {
        log.info("[AgentAPI] getEffectivePermissions conversationUid={} agentUid={}", conversationUid, agentUid);
        return PermissionApiMapper.toResponse(permissionAppService.getEffectiveRules(conversationUid, agentUid));
    }

    @PutMapping("/permissions/agent-settings/{agentUid}")
    public PermissionRulesResponse updateAgentPermissions(@PathVariable String agentUid,
                                                          @RequestBody(required = false) UpdatePermissionRulesRequest request) {
        log.info("[AgentAPI] updateAgentPermissions agentUid={} rules={}", agentUid,
                request == null || request.rules() == null ? 0 : request.rules().size());
        return PermissionApiMapper.toResponse(permissionAppService.updateAgentRules(agentUid, PermissionApiMapper.toParam(request)));
    }

    @PutMapping("/permissions/user-settings")
    public PermissionRulesResponse updateUserPermissions(@RequestParam(required = false, defaultValue = "") String agentUid,
                                                         @RequestBody(required = false) UpdatePermissionRulesRequest request) {
        log.info("[AgentAPI] updateUserPermissions agentUid={} rules={}", agentUid,
                request == null || request.rules() == null ? 0 : request.rules().size());
        return PermissionApiMapper.toResponse(permissionAppService.updateUserRules(agentUid, PermissionApiMapper.toParam(request)));
    }
}
