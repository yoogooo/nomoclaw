package ai.nomoclaw.bot.store.query;

import java.util.List;

public record PageSlice<T>(
        List<T> items,
        boolean hasMore
) {
}
