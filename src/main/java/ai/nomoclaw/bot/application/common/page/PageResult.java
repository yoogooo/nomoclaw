package ai.nomoclaw.bot.application.common.page;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        long totalPages
) {
}
