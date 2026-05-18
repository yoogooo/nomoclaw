package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.agentprofile.model.CreateAgentParam;
import ai.nomoclaw.bot.agentprofile.model.CreateAgentTipParam;
import ai.nomoclaw.bot.skill.model.CreateSkillParam;
import ai.nomoclaw.bot.skill.model.ImportSkillFromUrlParam;
import ai.nomoclaw.bot.skill.model.UpdateSkillBindingsParam;
import ai.nomoclaw.bot.agentprofile.model.UpdateAgentBasicInfoParam;
import ai.nomoclaw.bot.agentprofile.model.UpdateAgentTipParam;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogAgentDto;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogGroupDto;
import ai.nomoclaw.bot.agentprofile.model.AgentDocDto;
import ai.nomoclaw.bot.skill.model.AgentSkillDto;
import ai.nomoclaw.bot.agentprofile.model.AgentTipDto;
import ai.nomoclaw.bot.agentprofile.model.AgentToolDto;
import ai.nomoclaw.bot.skill.model.GlobalSkillDto;
import ai.nomoclaw.bot.skill.model.SkillBindingsDto;
import ai.nomoclaw.bot.mcp.AgentMcpToolDto;
import ai.nomoclaw.bot.mcp.McpApplicationService;
import ai.nomoclaw.bot.skill.SkillService;
import ai.nomoclaw.bot.skill.SkillImportService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AgentCatalogAppService {

    private final AgentApplicationService facade;
    private final SkillImportService skillImportApplicationService;
    private final SkillService skillService;
    private final AgentTipApplicationService agentTipApplicationService;
    private final McpApplicationService mcpApplicationService;

    public AgentCatalogAppService(AgentApplicationService facade,
                                  SkillImportService skillImportApplicationService,
                                  SkillService skillService,
                                  AgentTipApplicationService agentTipApplicationService,
                                  McpApplicationService mcpApplicationService) {
        this.facade = facade;
        this.skillImportApplicationService = skillImportApplicationService;
        this.skillService = skillService;
        this.agentTipApplicationService = agentTipApplicationService;
        this.mcpApplicationService = mcpApplicationService;
    }

    public List<AgentCatalogGroupDto> listAgentGroups() {
        return facade.listAgentGroups();
    }

    public List<GlobalSkillDto> listSkills() {
        return skillService.listSkills();
    }

    public GlobalSkillDto updateSkillStatus(String skillKey, boolean enabled) {
        return skillService.updateSkillStatus(skillKey, enabled);
    }

    public SkillBindingsDto getSkillBindings(String skillKey) {
        return skillService.getSkillBindings(skillKey);
    }

    public SkillBindingsDto updateSkillBindings(String skillKey, UpdateSkillBindingsParam command) {
        return skillService.updateSkillBindings(skillKey, command);
    }

    public void deleteSkill(String skillKey) {
        skillService.deleteSkill(skillKey);
    }

    public AgentCatalogAgentDto createAgent(CreateAgentParam command) {
        return facade.createAgent(command);
    }

    public void deleteAgent(String agentUid) {
        facade.deleteAgent(agentUid);
    }

    public AgentCatalogAgentDto updateAgentBasicInfo(String agentUid, UpdateAgentBasicInfoParam command) {
        return facade.updateAgentBasicInfo(agentUid, command);
    }

    public AgentSkillDto importSkillFromUrl(String agentUid, ImportSkillFromUrlParam command) {
        return skillImportApplicationService.importSkillFromUrl(agentUid, command);
    }

    public AgentSkillDto importSkillArchive(String agentUid, MultipartFile file, boolean attachToAgent) {
        return skillImportApplicationService.importSkillArchive(agentUid, file, attachToAgent);
    }

    public AgentSkillDto createSkill(String agentUid, CreateSkillParam command) {
        return skillImportApplicationService.createSkill(agentUid, command);
    }

    public List<AgentToolDto> listAgentTools(String agentUid) {
        return facade.listAgentTools(agentUid);
    }

    public AgentToolDto updateAgentToolStatus(String agentUid, String toolKey, boolean enabled) {
        return facade.updateAgentToolStatus(agentUid, toolKey, enabled);
    }

    public List<AgentMcpToolDto> listAgentMcpTools(String agentUid) {
        return mcpApplicationService.listAgentTools(agentUid);
    }

    public AgentMcpToolDto updateAgentMcpToolStatus(String agentUid, String toolKey, boolean enabled) {
        return mcpApplicationService.updateAgentToolStatus(agentUid, toolKey, enabled);
    }

    public List<AgentDocDto> listAgentDocs(String agentUid) {
        return facade.listAgentDocs(agentUid);
    }

    public AgentDocDto updateAgentDoc(String agentUid, String docKey, String content) {
        return facade.updateAgentDoc(agentUid, docKey, content);
    }

    public List<AgentTipDto> listAgentTips(String agentUid) {
        return agentTipApplicationService.listAgentTips(agentUid);
    }

    public AgentTipDto createAgentTip(String agentUid, CreateAgentTipParam command) {
        return agentTipApplicationService.createAgentTip(agentUid, command);
    }

    public AgentTipDto updateAgentTip(String agentUid, String tipUid, UpdateAgentTipParam command) {
        return agentTipApplicationService.updateAgentTip(agentUid, tipUid, command);
    }

    public void deleteAgentTip(String agentUid, String tipUid) {
        agentTipApplicationService.deleteAgentTip(agentUid, tipUid);
    }
}
