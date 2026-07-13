package ai.nomoclaw.bot.api.controller.system;

import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.api.dto.system.request.OpenFileRequest;
import ai.nomoclaw.bot.api.dto.system.request.TestModelProviderRequest;
import ai.nomoclaw.bot.api.dto.system.request.UpdateChannelConfigRequest;
import ai.nomoclaw.bot.api.dto.system.request.UpdateModelConfigRequest;
import ai.nomoclaw.bot.api.dto.system.response.ChannelConfigResponse;
import ai.nomoclaw.bot.api.dto.system.response.ChannelTargetSearchResponse;
import ai.nomoclaw.bot.api.dto.system.response.ModelCatalogStatusResponse;
import ai.nomoclaw.bot.api.dto.system.response.ModelConfigResponse;
import ai.nomoclaw.bot.api.dto.system.response.ModelProviderTestResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemConfigResponse;
import ai.nomoclaw.bot.api.mapper.SystemApiMapper;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.orchestrator.SystemAppService;
import ai.nomoclaw.bot.scheduler.CronChannelTargetDirectoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * System configuration and model configuration endpoints.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class SystemController {

    private final ModelConfigAppService modelConfigAppService;
    private final SystemAppService systemAppService;
    private final CronChannelTargetDirectoryService cronChannelTargetDirectoryService;

    public SystemController(ModelConfigAppService modelConfigAppService,
                            SystemAppService systemAppService,
                            CronChannelTargetDirectoryService cronChannelTargetDirectoryService) {
        this.modelConfigAppService = modelConfigAppService;
        this.systemAppService = systemAppService;
        this.cronChannelTargetDirectoryService = cronChannelTargetDirectoryService;
    }

    @GetMapping("/system/config")
    public SystemConfigResponse getSystemConfig() {
        log.info("[AgentAPI] getSystemConfig");
        return SystemApiMapper.toSystemConfig(systemAppService.getSystemConfig());
    }

    @GetMapping("/system/channels")
    public ChannelConfigResponse getChannelConfig() {
        log.info("[AgentAPI] getChannelConfig");
        return SystemApiMapper.toChannelConfig(systemAppService.getChannelConfig());
    }

    @GetMapping("/system/channels/targets/search")
    public ChannelTargetSearchResponse searchChannelTargets(@RequestParam String channel,
                                                            @RequestParam(required = false, defaultValue = "") String keyword,
                                                            @RequestParam(required = false, defaultValue = "") String botId,
                                                            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.info("[AgentAPI] searchChannelTargets channel={} keyword={} botId={} limit={}", channel, keyword, botId, limit);
        return SystemApiMapper.toChannelTargetSearch(cronChannelTargetDirectoryService.search(channel, keyword, botId, limit));
    }

    @GetMapping("/system/models")
    public ModelConfigResponse getModelConfig() {
        log.info("[AgentAPI] getModelConfig");
        return SystemApiMapper.toModelConfig(modelConfigAppService.getModelConfig());
    }

    @GetMapping("/system/models/available")
    public ModelConfigResponse getAvailableModelConfig() {
        log.info("[AgentAPI] getAvailableModelConfig");
        return SystemApiMapper.toModelConfig(modelConfigAppService.getAvailableModelConfig());
    }

    @GetMapping("/system/models/catalog/status")
    public ModelCatalogStatusResponse getModelCatalogStatus() {
        log.info("[AgentAPI] getModelCatalogStatus");
        return SystemApiMapper.toModelCatalogStatus(modelConfigAppService.getCatalogStatus());
    }

    @PostMapping("/system/models/catalog/refresh")
    public ModelCatalogStatusResponse refreshModelCatalog() {
        log.info("[AgentAPI] refreshModelCatalog");
        return SystemApiMapper.toModelCatalogStatus(modelConfigAppService.refreshModelCatalog());
    }

    @PutMapping("/system/channels")
    public ChannelConfigResponse updateChannelConfig(@RequestBody(required = false) UpdateChannelConfigRequest request) {
        log.info("[AgentAPI] updateChannelConfig");
        return SystemApiMapper.toChannelConfig(systemAppService.updateChannelConfig(SystemApiMapper.toChannelConfig(request)));
    }

    @PutMapping("/system/models")
    public ModelConfigResponse updateModelConfig(@RequestBody(required = false) UpdateModelConfigRequest request) {
        log.info("[AgentAPI] updateModelConfig");
        return SystemApiMapper.toModelConfig(modelConfigAppService.updateModelConfig(SystemApiMapper.toModelConfig(request)));
    }

    @PostMapping("/system/models/providers/{providerId}/load-local")
    public ModelConfigResponse loadLocalModels(@PathVariable String providerId) {
        log.info("[AgentAPI] loadLocalModels providerId={}", providerId);
        return SystemApiMapper.toModelConfig(modelConfigAppService.loadLocalModels(providerId));
    }

    @PostMapping("/system/models/providers/test")
    public ModelProviderTestResponse testModelProvider(@RequestBody(required = false) TestModelProviderRequest request) {
        String providerId = request == null ? "" : request.providerId();
        log.info("[AgentAPI] testModelProvider providerId={}", providerId);
        ModelConfigAppService.ProbeResult result = modelConfigAppService.testProviderConnection(
                providerId,
                request == null ? "" : request.baseUrl(),
                request == null ? "" : request.apiKey()
        );
        return new ModelProviderTestResponse(result.success(), result.message());
    }

    @PostMapping("/system/models/codex/login")
    public ModelProviderTestResponse startCodexLogin() {
        log.info("[AgentAPI] startCodexLogin");
        ModelConfigAppService.ProbeResult result = modelConfigAppService.startCodexLogin();
        return new ModelProviderTestResponse(result.success(), result.message());
    }

    @PostMapping("/files/open")
    public SimpleResponse openFile(@Valid @RequestBody OpenFileRequest request) {
        log.info("[AgentAPI] openFile path={}", request.path());
        systemAppService.openFile(request.path());
        return new SimpleResponse("opened");
    }

    @GetMapping("/files/content")
    public ResponseEntity<Resource> readFileContent(@RequestParam String path) {
        log.info("[AgentAPI] readFileContent path={}", path);
        Path filePath = systemAppService.resolvePreviewFile(path);
        FileSystemResource resource = new FileSystemResource(filePath);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(filePath);
            if (detected != null && !detected.isBlank()) {
                mediaType = MediaType.parseMediaType(detected);
            }
        } catch (Exception ex) {
            log.debug("[AgentAPI] probeContentType failed for {}", filePath, ex);
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }
}
