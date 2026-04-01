package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.command.CreateAgentCommand;
import ai.nomoclaw.bot.application.command.CreateAgentTipCommand;
import ai.nomoclaw.bot.application.command.CreateSkillCommand;
import ai.nomoclaw.bot.application.command.ImportSkillFromUrlCommand;
import ai.nomoclaw.bot.application.command.UpdateAgentBasicInfoCommand;
import ai.nomoclaw.bot.application.dto.AgentCatalogAgentDto;
import ai.nomoclaw.bot.application.dto.AgentCatalogGroupDto;
import ai.nomoclaw.bot.application.dto.AgentSkillDto;
import ai.nomoclaw.bot.application.dto.AgentTipDto;
import ai.nomoclaw.bot.application.dto.AgentToolDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AgentCatalogAppService {

    private final AgentApplicationService facade;
    private final SkillImportApplicationService skillImportApplicationService;

    public AgentCatalogAppService(AgentApplicationService facade,
                                  SkillImportApplicationService skillImportApplicationService) {
        this.facade = facade;
        this.skillImportApplicationService = skillImportApplicationService;
    }

    public List<AgentCatalogGroupDto> listAgentGroups() {
        return facade.listAgentGroups();
    }

    public List<AgentSkillDto> listAgentSkills(String agentUid) {
        return facade.listAgentSkills(agentUid);
    }

    public AgentCatalogAgentDto createAgent(CreateAgentCommand command) {
        return facade.createAgent(command);
    }

    public AgentCatalogAgentDto updateAgentBasicInfo(String agentUid, UpdateAgentBasicInfoCommand command) {
        return facade.updateAgentBasicInfo(agentUid, command);
    }

    public AgentSkillDto updateAgentSkillStatus(String agentUid, String skillKey, boolean enabled) {
        return facade.updateAgentSkillStatus(agentUid, skillKey, enabled);
    }

    public AgentSkillDto importSkillFromUrl(String agentUid, ImportSkillFromUrlCommand command) {
        return skillImportApplicationService.importSkillFromUrl(agentUid, command);
    }

    public AgentSkillDto importSkillArchive(String agentUid, MultipartFile file, boolean attachToAgent) {
        return skillImportApplicationService.importSkillArchive(agentUid, file, attachToAgent);
    }

    public AgentSkillDto createSkill(String agentUid, CreateSkillCommand command) {
        return skillImportApplicationService.createSkill(agentUid, command);
    }

    public List<AgentToolDto> listAgentTools(String agentUid) {
        return facade.listAgentTools(agentUid);
    }

    public AgentToolDto updateAgentToolStatus(String agentUid, String toolKey, boolean enabled) {
        return facade.updateAgentToolStatus(agentUid, toolKey, enabled);
    }

    public List<AgentTipDto> listAgentTips(String agentUid) {
        return facade.listAgentTips(agentUid);
    }

    public AgentTipDto createAgentTip(String agentUid, CreateAgentTipCommand command) {
        return facade.createAgentTip(agentUid, command);
    }

    public void deleteAgentTip(String agentUid, String tipUid) {
        facade.deleteAgentTip(agentUid, tipUid);
    }
}
