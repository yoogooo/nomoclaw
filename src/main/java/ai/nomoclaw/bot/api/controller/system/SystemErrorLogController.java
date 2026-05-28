package ai.nomoclaw.bot.api.controller.system;

import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogSummaryResponse;
import ai.nomoclaw.bot.api.mapper.SystemApiMapper;
import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * System error log query endpoints.
 */
@RestController
@RequestMapping("/api/system/error-logs")
@Slf4j
public class SystemErrorLogController {

    private final SystemErrorLogService systemErrorLogService;

    public SystemErrorLogController(SystemErrorLogService systemErrorLogService) {
        this.systemErrorLogService = systemErrorLogService;
    }

    @GetMapping
    public PageResponse<SystemErrorLogResponse> listErrorLogs(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        log.info("[AgentAPI] listSystemErrorLogs keyword={} page={} pageSize={}", keyword, page, pageSize);
        return SystemApiMapper.toSystemErrorLogPage(systemErrorLogService.pageLatest(page, pageSize, keyword));
    }

    @GetMapping("/summary")
    public SystemErrorLogSummaryResponse getErrorLogSummary() {
        log.info("[AgentAPI] getSystemErrorLogSummary");
        return SystemApiMapper.toSystemErrorLogSummary(systemErrorLogService.summary());
    }
}
