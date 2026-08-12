package ai.nomoclaw.bot.llm.debug;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.time.Duration;

/**
 * Builds LangChain4j HTTP clients that persist the provider-ready protocol payloads.
 */
public class TraceHttpClientBuilder implements HttpClientBuilder {

    private final LlmTraceRecorder recorder;
    private final String protocolType;
    private final HttpClientBuilder delegate = JdkHttpClient.builder();

    public TraceHttpClientBuilder(LlmTraceRecorder recorder, String protocolType) {
        this.recorder = recorder;
        this.protocolType = protocolType;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        return new TraceHttpClient(delegate.build(), recorder, protocolType);
    }

    private static final class TraceHttpClient implements HttpClient {
        private final HttpClient delegate;
        private final LlmTraceRecorder recorder;
        private final String protocolType;

        private TraceHttpClient(HttpClient delegate, LlmTraceRecorder recorder, String protocolType) {
            this.delegate = delegate;
            this.recorder = recorder;
            this.protocolType = protocolType;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            LlmTraceRecorder.TraceHandle trace = recorder.activeTrace();
            recordRequest(trace, request);
            try {
                SuccessfulHttpResponse response = delegate.execute(request);
                recorder.recordRawResponse(trace, response.statusCode(), response.body());
                return response;
            } catch (RuntimeException exception) {
                throw exception;
            }
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            LlmTraceRecorder.TraceHandle trace = recorder.activeTrace();
            recordRequest(trace, request);
            delegate.execute(request, parser, new ServerSentEventListener() {
                @Override
                public void onOpen(SuccessfulHttpResponse response) {
                    recorder.recordRawResponse(trace, response.statusCode(), response.body());
                    listener.onOpen(response);
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    recorder.appendRawStreamEvent(trace, event.event(), event.data());
                    listener.onEvent(event, context);
                }

                @Override
                public void onError(Throwable throwable) {
                    listener.onError(throwable);
                }

                @Override
                public void onClose() {
                    listener.onClose();
                }
            });
        }

        private void recordRequest(LlmTraceRecorder.TraceHandle trace, HttpRequest request) {
            recorder.recordRawRequest(trace, protocolType, request.url(), request.method().name(), request.headers(), request.body());
        }
    }
}
