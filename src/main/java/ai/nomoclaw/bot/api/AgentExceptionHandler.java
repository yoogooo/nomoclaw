package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.api.dto.common.response.SimpleResponse;
import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
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
import ai.nomoclaw.bot.util.LocalizedMessages;

@RestControllerAdvice
@Slf4j
public class AgentExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:2MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:40MB}")
    private String maxRequestSize;

    private final LocalizedMessages localizedMessages;
    private final SystemErrorLogService systemErrorLogService;

    public AgentExceptionHandler(LocalizedMessages localizedMessages,
                                 SystemErrorLogService systemErrorLogService) {
        this.localizedMessages = localizedMessages;
        this.systemErrorLogService = systemErrorLogService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SimpleResponse handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[AgentAPI][400] illegal argument: {}", ex.getMessage());
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
        String localizedMessage = localizedMessages.get("api.error.uploadTooLarge", maxFileSize, maxRequestSize);
        return new SimpleResponse(localizedMessage);
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
    public SimpleResponse handleInternal(Exception ex, HttpServletRequest request) {
        log.error("[AgentAPI][500] unhandled exception", ex);
        systemErrorLogService.recordException(
                "ERROR",
                "HTTP",
                "HTTP_INTERNAL_ERROR",
                "HTTP 500 未处理异常",
                "%s %s failed: %s".formatted(
                        request == null ? "" : request.getMethod(),
                        request == null ? "" : request.getRequestURI(),
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                ).trim(),
                ex
        );
        return new SimpleResponse(ex.getMessage() == null ? "internal error" : ex.getMessage());
    }
}
