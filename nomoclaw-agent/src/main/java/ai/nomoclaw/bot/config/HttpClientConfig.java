package ai.nomoclaw.bot.config;

import ai.nomoclaw.bot.llm.config.LlmProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    @Primary
    public HttpClient appHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean(name = "llmHttpClient")
    public HttpClient llmHttpClient(LlmProperties llmProperties) {
        Duration connectTimeout = llmProperties.getConnectTimeout();
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
