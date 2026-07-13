package ai.nomoclaw.bot.api.dto.system.request;

public record TestModelProviderRequest(
        String providerId,
        String baseUrl,
        String apiKey
) {
}
