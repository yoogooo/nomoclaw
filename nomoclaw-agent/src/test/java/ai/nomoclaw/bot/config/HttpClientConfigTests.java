package ai.nomoclaw.bot.config;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientConfigTests {

    @Test
    void llmHttpClientShouldUseDedicatedFiveSecondConnectTimeout() {
        LlmProperties properties = new LlmProperties();
        HttpClient httpClient = new HttpClientConfig().llmHttpClient(properties);

        assertEquals(Duration.ofSeconds(5), httpClient.connectTimeout().orElseThrow());
    }
}
