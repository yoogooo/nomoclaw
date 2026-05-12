package ai.nomoclaw.bot.api.controller.skill;

import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.dto.skill.request.CreateSkillRequest;
import ai.nomoclaw.bot.api.dto.skill.request.ImportSkillFromUrlRequest;
import ai.nomoclaw.bot.api.dto.skill.request.UpdateSkillBindingsRequest;
import ai.nomoclaw.bot.api.dto.skill.request.UpdateSkillStatusRequest;
import ai.nomoclaw.bot.api.dto.skill.response.AgentSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.GlobalSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.SkillBindingsResponse;
import ai.nomoclaw.bot.api.mapper.SkillApiMapper;
import ai.nomoclaw.bot.orchestrator.AgentCatalogAppService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Global skill catalog and skill import endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class SkillController {

    private final AgentCatalogAppService agentCatalogAppService;

    public SkillController(AgentCatalogAppService agentCatalogAppService) {
        this.agentCatalogAppService = agentCatalogAppService;
    }

    @GetMapping("/skills")
    public List<GlobalSkillResponse> listSkills() {
        log.info("[AgentAPI] listSkills");
        return SkillApiMapper.toGlobalSkills(agentCatalogAppService.listSkills());
    }

    @GetMapping("/skills/{skillKey}/bindings")
    public SkillBindingsResponse getSkillBindings(@PathVariable String skillKey) {
        log.info("[AgentAPI] getSkillBindings skillKey={}", skillKey);
        return SkillApiMapper.toSkillBindings(agentCatalogAppService.getSkillBindings(skillKey));
    }

    @PatchMapping("/skills/{skillKey}")
    public GlobalSkillResponse updateSkillStatus(@PathVariable String skillKey,
                                                 @Valid @RequestBody UpdateSkillStatusRequest request) {
        log.info("[AgentAPI] updateSkillStatus skillKey={} enabled={}", skillKey, request.enabled());
        return SkillApiMapper.toGlobalSkill(agentCatalogAppService.updateSkillStatus(skillKey, request.enabled()));
    }

    @PutMapping("/skills/{skillKey}/bindings")
    public SkillBindingsResponse updateSkillBindings(@PathVariable String skillKey,
                                                     @Valid @RequestBody UpdateSkillBindingsRequest request) {
        log.info("[AgentAPI] updateSkillBindings skillKey={} enabled={} agents={}",
                skillKey,
                request.enabled(),
                request.agentBindings() == null ? 0 : request.agentBindings().size());
        return SkillApiMapper.toSkillBindings(agentCatalogAppService.updateSkillBindings(skillKey, SkillApiMapper.toCommand(request)));
    }

    @DeleteMapping("/skills/{skillKey}")
    public SimpleResponse deleteSkill(@PathVariable String skillKey) {
        log.info("[AgentAPI] deleteSkill skillKey={}", skillKey);
        agentCatalogAppService.deleteSkill(skillKey);
        return new SimpleResponse("deleted");
    }

    @PostMapping("/agents/{agentUid}/skills/import-url")
    public AgentSkillResponse importSkillFromUrl(@PathVariable String agentUid,
                                                 @RequestBody(required = false) ImportSkillFromUrlRequest request) {
        log.info("[AgentAPI] importSkillFromUrl agentUid={} url={} attachToAgent={}",
                agentUid,
                request == null ? null : request.url(),
                request != null && Boolean.TRUE.equals(request.attachToAgent()));
        return SkillApiMapper.toAgentSkill(agentCatalogAppService.importSkillFromUrl(agentUid, SkillApiMapper.toCommand(request)));
    }

    @PostMapping(value = "/agents/{agentUid}/skills/import-archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentSkillResponse importSkillArchive(@PathVariable String agentUid,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam(name = "attachToAgent", defaultValue = "true") boolean attachToAgent) {
        log.info("[AgentAPI] importSkillArchive agentUid={} fileName={} attachToAgent={}",
                agentUid,
                file == null ? null : file.getOriginalFilename(),
                attachToAgent);
        return SkillApiMapper.toAgentSkill(agentCatalogAppService.importSkillArchive(agentUid, file, attachToAgent));
    }

    @PostMapping("/agents/{agentUid}/skills/create")
    public AgentSkillResponse createSkill(@PathVariable String agentUid,
                                          @RequestBody(required = false) CreateSkillRequest request) {
        log.info("[AgentAPI] createSkill agentUid={} skillKey={} attachToAgent={}",
                agentUid,
                request == null ? null : request.skillKey(),
                request != null && Boolean.TRUE.equals(request.attachToAgent()));
        return SkillApiMapper.toAgentSkill(agentCatalogAppService.createSkill(agentUid, SkillApiMapper.toCommand(request)));
    }
}
