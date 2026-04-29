package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/error-logs")
@Slf4j
public class SystemErrorLogController {

    private final SystemErrorLogService systemErrorLogService;

    public SystemErrorLogController(SystemErrorLogService systemErrorLogService) {
        this.systemErrorLogService = systemErrorLogService;
    }

    @GetMapping
    public List<SystemErrorLogResponse> listErrorLogs(@RequestParam(name = "limit", required = false) Integer limit) {
        log.info("[AgentAPI] listSystemErrorLogs limit={}", limit);
        return ApiDtoMapper.toSystemErrorLogs(systemErrorLogService.listLatest(limit));
    }

    @GetMapping("/summary")
    public SystemErrorLogSummaryResponse getErrorLogSummary() {
        log.info("[AgentAPI] getSystemErrorLogSummary");
        return ApiDtoMapper.toSystemErrorLogSummary(systemErrorLogService.summary());
    }
}
