package ai.nomoclaw.bot.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class AgentExceptionHandler {

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
