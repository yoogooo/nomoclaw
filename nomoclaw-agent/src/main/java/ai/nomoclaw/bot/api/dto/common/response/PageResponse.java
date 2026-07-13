package ai.nomoclaw.bot.api.dto.common.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        long totalPages
) {
}
