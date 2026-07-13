package ai.nomoclaw.bot.api.dto.system.response;

import java.util.List;

public record ChannelTargetSearchResponse(
        List<Item> items,
        String error
) {
    public record Item(
            String label,
            String target,
            String kind,
            String source
    ) {
    }
}
