package ai.nomoclaw.bot.api.controller.agent;

import ai.nomoclaw.bot.api.dto.agent.request.CreateAgentRequest;
import ai.nomoclaw.bot.api.dto.agent.request.CreateAgentTipRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentBasicInfoRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentDocRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentTipRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentToolStatusRequest;
import ai.nomoclaw.bot.api.dto.agent.response.AgentCatalogAgentResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentCatalogGroupResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentDocResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentMcpToolResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentTipResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentToolResponse;
import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.mapper.AgentApiMapper;
import ai.nomoclaw.bot.orchestrator.AgentCatalogAppService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent profile, tool, tip, and doc endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class AgentController {

    private final AgentCatalogAppService agentCatalogAppService;

    public AgentController(AgentCatalogAppService agentCatalogAppService) {
        this.agentCatalogAppService = agentCatalogAppService;
    }

    @GetMapping("/agent-groups")
    public List<AgentCatalogGroupResponse> listAgentGroups() {
        log.info("[AgentAPI] listAgentGroups");
        return AgentApiMapper.toAgentCatalogGroups(agentCatalogAppService.listAgentGroups());
    }

    @PostMapping("/agents")
    public AgentCatalogAgentResponse createAgent(@Valid @RequestBody CreateAgentRequest request) {
        log.info("[AgentAPI] createAgent agentName={} displayName={}", request.agentName(), request.displayName());
        return AgentApiMapper.toAgentCatalogAgent(agentCatalogAppService.createAgent(AgentApiMapper.toCommand(request)));
    }

    @DeleteMapping("/agents/{agentUid}")
    public SimpleResponse deleteAgent(@PathVariable String agentUid) {
        log.info("[AgentAPI] deleteAgent agentUid={}", agentUid);
        agentCatalogAppService.deleteAgent(agentUid);
        return new SimpleResponse("deleted");
    }

    @PatchMapping("/agents/{agentUid}/basic")
    public AgentCatalogAgentResponse updateAgentBasicInfo(@PathVariable String agentUid,
                                                          @Valid @RequestBody UpdateAgentBasicInfoRequest request) {
        log.info("[AgentAPI] updateAgentBasicInfo agentUid={} displayName={} avatar={} avatarColor={} modelProvider={} modelName={}",
                agentUid, request.displayName(), request.avatar(), request.avatarColor(), request.modelProvider(), request.modelName());
        return AgentApiMapper.toAgentCatalogAgent(agentCatalogAppService.updateAgentBasicInfo(agentUid, AgentApiMapper.toCommand(request)));
    }

    @GetMapping("/agents/{agentUid}/tools")
    public List<AgentToolResponse> listAgentTools(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentTools agentUid={}", agentUid);
        return AgentApiMapper.toAgentTools(agentCatalogAppService.listAgentTools(agentUid));
    }

    @PatchMapping("/agents/{agentUid}/tools/{toolKey}")
    public AgentToolResponse updateAgentToolStatus(@PathVariable String agentUid,
                                                   @PathVariable String toolKey,
                                                   @Valid @RequestBody UpdateAgentToolStatusRequest request) {
        log.info("[AgentAPI] updateAgentToolStatus agentUid={} toolKey={} enabled={}",
                agentUid, toolKey, request.enabled());
        return AgentApiMapper.toAgentTool(agentCatalogAppService.updateAgentToolStatus(agentUid, toolKey, request.enabled()));
    }

    @GetMapping("/agents/{agentUid}/mcp-tools")
    public List<AgentMcpToolResponse> listAgentMcpTools(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentMcpTools agentUid={}", agentUid);
        return AgentApiMapper.toAgentMcpTools(agentCatalogAppService.listAgentMcpTools(agentUid));
    }

    @PatchMapping("/agents/{agentUid}/mcp-tools/{toolKey}")
    public AgentMcpToolResponse updateAgentMcpToolStatus(@PathVariable String agentUid,
                                                         @PathVariable String toolKey,
                                                         @Valid @RequestBody UpdateAgentToolStatusRequest request) {
        log.info("[AgentAPI] updateAgentMcpToolStatus agentUid={} toolKey={} enabled={}",
                agentUid, toolKey, request.enabled());
        return AgentApiMapper.toAgentMcpTool(agentCatalogAppService.updateAgentMcpToolStatus(agentUid, toolKey, request.enabled()));
    }

    @GetMapping("/agents/{agentUid}/tips")
    public List<AgentTipResponse> listAgentTips(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentTips agentUid={}", agentUid);
        return AgentApiMapper.toAgentTips(agentCatalogAppService.listAgentTips(agentUid));
    }

    @PostMapping("/agents/{agentUid}/tips")
    public AgentTipResponse createAgentTip(@PathVariable String agentUid,
                                           @RequestBody(required = false) CreateAgentTipRequest request) {
        log.info("[AgentAPI] createAgentTip agentUid={} sourceConversationUid={} sourceMessageUid={} generateBestPractice={}",
                agentUid,
                request == null ? null : request.sourceConversationUid(),
                request == null ? null : request.sourceMessageUid(),
                request == null ? null : request.generateBestPractice());
        return AgentApiMapper.toAgentTip(agentCatalogAppService.createAgentTip(agentUid, AgentApiMapper.toCommand(request)));
    }

    @PutMapping("/agents/{agentUid}/tips/{tipUid}")
    public AgentTipResponse updateAgentTip(@PathVariable String agentUid,
                                           @PathVariable String tipUid,
                                           @RequestBody(required = false) UpdateAgentTipRequest request) {
        log.info("[AgentAPI] updateAgentTip agentUid={} tipUid={}", agentUid, tipUid);
        return AgentApiMapper.toAgentTip(agentCatalogAppService.updateAgentTip(agentUid, tipUid, AgentApiMapper.toCommand(request)));
    }

    @DeleteMapping("/agents/{agentUid}/tips/{tipUid}")
    public SimpleResponse deleteAgentTip(@PathVariable String agentUid, @PathVariable String tipUid) {
        log.info("[AgentAPI] deleteAgentTip agentUid={} tipUid={}", agentUid, tipUid);
        agentCatalogAppService.deleteAgentTip(agentUid, tipUid);
        return new SimpleResponse("deleted");
    }

    @GetMapping("/agents/{agentUid}/docs")
    public List<AgentDocResponse> listAgentDocs(@PathVariable String agentUid) {
        log.info("[AgentAPI] listAgentDocs agentUid={}", agentUid);
        return AgentApiMapper.toAgentDocs(agentCatalogAppService.listAgentDocs(agentUid));
    }

    @PutMapping("/agents/{agentUid}/docs/{docKey}")
    public AgentDocResponse updateAgentDoc(@PathVariable String agentUid,
                                           @PathVariable String docKey,
                                           @RequestBody(required = false) UpdateAgentDocRequest request) {
        log.info("[AgentAPI] updateAgentDoc agentUid={} docKey={}", agentUid, docKey);
        return AgentApiMapper.toAgentDoc(agentCatalogAppService.updateAgentDoc(
                agentUid,
                docKey,
                request == null ? "" : request.content()
        ));
    }
}
