package ai.nomoclaw.bot.application.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

public final class PageResults {
    private PageResults() {
    }

    public static <S, T> PageResult<T> fromMpPage(IPage<S> page, Function<S, T> converter) {
        List<T> items = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(
                items,
                page.getTotal(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getPages()
        );
    }
}
