package ai.nomoclaw.bot.api.controller.system;

import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.api.dto.system.response.TokenUsageOverviewResponse;
import ai.nomoclaw.bot.api.dto.system.response.TokenUsageRecordResponse;
import ai.nomoclaw.bot.orchestrator.TokenUsageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system/token-usage")
public class TokenUsageController {
    private final TokenUsageQueryService queryService;
    public TokenUsageController(TokenUsageQueryService queryService) { this.queryService = queryService; }

    @GetMapping("/overview")
    public TokenUsageOverviewResponse overview(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                               @RequestParam(required = false) String provider, @RequestParam(required = false) String model,
                                               @RequestParam(required = false) String scene) {
        return queryService.overview(parseFrom(from), parseTo(to), provider, model, scene);
    }

    @GetMapping
    public PageResponse<TokenUsageRecordResponse> page(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                        @RequestParam(required = false) String provider, @RequestParam(required = false) String model,
                                                        @RequestParam(required = false) String scene, @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        return queryService.page(parseFrom(from), parseTo(to), provider, model, scene, page, pageSize);
    }

    private LocalDateTime parseFrom(String value) { return value == null || value.isBlank() ? LocalDate.now().minusDays(29).atStartOfDay() : LocalDate.parse(value).atStartOfDay(); }
    private LocalDateTime parseTo(String value) { return value == null || value.isBlank() ? LocalDate.now().plusDays(1).atStartOfDay() : LocalDate.parse(value).plusDays(1).atStartOfDay(); }
}
