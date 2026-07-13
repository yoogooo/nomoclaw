package ai.nomoclaw.bot.common.page;

public record PageRequest(
        int page,
        int pageSize
) {
    public PageRequest normalize(int defaultPageSize, int maxPageSize) {
        int safePage = page <= 0 ? 1 : page;
        int safePageSize = pageSize <= 0 ? defaultPageSize : Math.min(pageSize, maxPageSize);
        return new PageRequest(safePage, safePageSize);
    }
}
