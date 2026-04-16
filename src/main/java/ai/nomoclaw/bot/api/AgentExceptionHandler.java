package ai.nomoclaw.bot.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class AgentExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:40MB}")
    private String maxRequestSize;

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SimpleResponse handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[AgentAPI][400] illegal argument: {}", ex.getMessage(), ex);
        return new SimpleResponse(ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public SimpleResponse handleNoResource(NoResourceFoundException ex) {
        log.debug("[AgentAPI][404] resource not found: {}", ex.getMessage());
        return new SimpleResponse("resource not found");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public SimpleResponse handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("[AgentAPI][413] upload too large: {}", ex.getMessage());
        return new SimpleResponse("上传内容过大：单文件上限 " + maxFileSize + "，总请求上限 " + maxRequestSize + "。");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        log.warn("[AgentAPI][SSE] client disconnected: {}", ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        log.info("[AgentAPI][SSE] request timeout: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public SimpleResponse handleInternal(Exception ex) {
        log.error("[AgentAPI][500] unhandled exception", ex);
        return new SimpleResponse(ex.getMessage() == null ? "internal error" : ex.getMessage());
    }
}
