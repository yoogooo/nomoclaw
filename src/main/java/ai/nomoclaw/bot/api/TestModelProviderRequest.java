package ai.nomoclaw.bot.api;

public record TestModelProviderRequest(
        String providerId,
        String baseUrl,
        String apiKey
) {
}
