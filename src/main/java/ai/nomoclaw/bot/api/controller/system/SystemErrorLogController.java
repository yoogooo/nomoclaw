package ai.nomoclaw.bot.api.controller.system;

import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogSummaryResponse;
import ai.nomoclaw.bot.api.mapper.SystemApiMapper;
import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public List<SystemErrorLogResponse> listErrorLogs(@RequestParam(name = "limit", required = false) Integer limit,
                                                       @RequestParam(name = "keyword", required = false) String keyword) {
        log.info("[AgentAPI] listSystemErrorLogs limit={} keyword={}", limit, keyword);
        return SystemApiMapper.toSystemErrorLogs(systemErrorLogService.listLatest(limit, keyword));
    }

    @GetMapping("/summary")
    public SystemErrorLogSummaryResponse getErrorLogSummary() {
        log.info("[AgentAPI] getSystemErrorLogSummary");
        return SystemApiMapper.toSystemErrorLogSummary(systemErrorLogService.summary());
    }
}
