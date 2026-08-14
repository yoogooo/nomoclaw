package ai.nomoclaw.bot.llm.codex;

import ai.nomoclaw.bot.llm.debug.LlmTraceRecorder;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexApiClientTests {

    @Test
    void streamShouldSurfaceUsageLimitExceptionWhenCodexReturns429() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CodexTokenProvider tokenProvider = mock(CodexTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("test-token");
        when(tokenProvider.installationId()).thenReturn("installation-1");
        when(tokenProvider.accountId()).thenReturn("");

        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn(new ByteArrayInputStream("""
                {"error":{"type":"usage_limit_reached","message":"The usage limit has been reached","plan_type":"plus","resets_at":1782908546,"resets_in_seconds":10271}}
                """.trim().getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        CodexApiClient client = new CodexApiClient(httpClient, tokenProvider, "", Duration.ofSeconds(5));
        AtomicReference<Throwable> error = new AtomicReference<>();

        client.stream(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hello")))
                        .build(),
                "gpt-5.5",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                    }
                }
        );

        CodexUsageLimitException exception = assertInstanceOf(CodexUsageLimitException.class, error.get());
        assertEquals(429, exception.statusCode());
        assertEquals("usage_limit_reached", exception.errorType());
        assertEquals("plus", exception.planType());
        assertEquals(1782908546L, exception.resetAtEpochSecond());
        assertEquals(10271L, exception.resetsInSeconds());
    }

    @Test
    void streamShouldPreserveModelTextWithoutTrimming() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CodexTokenProvider tokenProvider = mock(CodexTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("test-token");
        when(tokenProvider.installationId()).thenReturn("installation-1");
        when(tokenProvider.accountId()).thenReturn("");

        String modelText = "  # Heading\n\nBody  ";
        String sse = """
                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"  # Heading\\n\\nBody  "}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp-1","output":[{"type":"message","content":[{"type":"output_text","text":"  # Heading\\n\\nBody  "}]}]}}

                """;
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        CodexApiClient client = new CodexApiClient(httpClient, tokenProvider, "", Duration.ofSeconds(5));
        AtomicReference<String> streamedText = new AtomicReference<>("");
        AtomicReference<ChatResponse> completedResponse = new AtomicReference<>();

        client.stream(
                ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build(),
                "gpt-5.5",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        streamedText.updateAndGet(text -> text + partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        completedResponse.set(completeResponse);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throw new AssertionError(throwable);
                    }
                }
        );

        assertEquals(modelText, streamedText.get());
        assertEquals(modelText, completedResponse.get().aiMessage().text());
    }

    @Test
    void streamShouldPreserveWhitespaceOnlyMarkdownDeltas() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CodexTokenProvider tokenProvider = mock(CodexTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("test-token");
        when(tokenProvider.installationId()).thenReturn("installation-1");
        when(tokenProvider.accountId()).thenReturn("");

        String modelText = "### 北京\n\n| 日期 | 天气 |\n|---|---|\n| 8月12日 | 大雨 |\n\n- 携带雨具";
        String sse = """
                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"### 北京"}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"\\n\\n"}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"| 日期 | 天气 |\\n|---|---|\\n| 8月12日 | 大雨 |"}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"\\n\\n"}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"- 携带雨具"}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp-1","output":[{"type":"message","content":[{"type":"output_text","text":"### 北京\\n\\n| 日期 | 天气 |\\n|---|---|\\n| 8月12日 | 大雨 |\\n\\n- 携带雨具"}]}]}}

                """;
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        CodexApiClient client = new CodexApiClient(httpClient, tokenProvider, "", Duration.ofSeconds(5));
        AtomicReference<String> streamedText = new AtomicReference<>("");
        AtomicReference<ChatResponse> completedResponse = new AtomicReference<>();

        client.stream(
                ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build(),
                "gpt-5.5",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        streamedText.updateAndGet(text -> text + partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        completedResponse.set(completeResponse);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throw new AssertionError(throwable);
                    }
                }
        );

        assertEquals(modelText, streamedText.get());
        assertEquals(modelText, completedResponse.get().aiMessage().text());
    }

    @Test
    void streamShouldRecordTheActualHttpRequestAndFinalSseMessageBodies() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CodexTokenProvider tokenProvider = mock(CodexTokenProvider.class);
        LlmTraceRecorder traceRecorder = mock(LlmTraceRecorder.class);
        LlmTraceRecorder.TraceHandle trace = new LlmTraceRecorder.TraceHandle("trace-1", "request-1", "conversation-1", 1L);
        when(tokenProvider.accessToken()).thenReturn("test-token");
        when(tokenProvider.installationId()).thenReturn("installation-1");
        when(tokenProvider.accountId()).thenReturn("");
        when(traceRecorder.activeTrace()).thenReturn(trace);

        String finalMessage = "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp-1\",\"output\":[]}}";
        LlmTraceRecorder.RawStreamEvent rawStreamEvent = new LlmTraceRecorder.RawStreamEvent(
                1, "response.completed", finalMessage);
        when(traceRecorder.newRawStreamEvent(1, "response.completed", finalMessage)).thenReturn(rawStreamEvent);
        String sse = "event: response.completed\n"
                + "data: " + finalMessage + "\n\n";
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        CodexApiClient client = new CodexApiClient(httpClient, tokenProvider, "", Duration.ofSeconds(5), traceRecorder);
        client.stream(ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build(), "gpt-5.5",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throw new AssertionError(throwable);
                    }
                });

        String expectedRequestBody = "{\"model\":\"gpt-5.5\",\"instructions\":\"\",\"stream\":true,\"store\":false,"
                + "\"input\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
        verify(traceRecorder).recordRawRequest(eq(trace), eq("codex-responses"), any(String.class), eq("POST"),
                any(), eq(expectedRequestBody));
        verify(traceRecorder).recordRawStreamingResponse(trace, 200, finalMessage, List.of(rawStreamEvent));
    }
}
