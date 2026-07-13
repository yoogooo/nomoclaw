package ai.nomoclaw.bot.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ai.nomoclaw.bot.util.JsonUtil;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpApiLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Set.of(
            "password",
            "passwd",
            "pwd",
            "token",
            "access_token",
            "refresh_token",
            "authorization",
            "api_key",
            "apikey",
            "secret",
            "secret_key",
            "client_secret"
    ));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean isApi = uri.startsWith("/api/");
        long start = System.currentTimeMillis();
        HttpServletRequest requestToUse = request;
        if (isApi && !(request instanceof ContentCachingRequestWrapper)) {
            requestToUse = new ContentCachingRequestWrapper(request, 16 * 1024);
        }
        if (isApi && requiresJsonContentType(requestToUse) && !hasAllowedContentType(requestToUse)) {
            log.warn("[HTTP][REJECT] method={} uri={} contentType={} reason=application/json required",
                    request.getMethod(), uri, request.getContentType());
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Content-Type must be application/json or multipart/form-data\"}");
            return;
        }
        if (isApi) {
            log.info("[HTTP][IN] method={} uri={} query={} params={} body={} remote={}",
                    request.getMethod(), uri, request.getQueryString(), formatParameters(requestToUse),
                    requestToUse instanceof ContentCachingRequestWrapper cachingRequest
                            ? summarizeRequestBody(cachingRequest)
                            : "",
                    request.getRemoteAddr());
        }
        try {
            filterChain.doFilter(requestToUse, response);
        } finally {
            if (isApi) {
                int status = response.getStatus();
                long costMs = System.currentTimeMillis() - start;
                String requestBody = requestToUse instanceof ContentCachingRequestWrapper cachingRequest
                        ? summarizeRequestBody(cachingRequest)
                        : "";
                if (status >= 500) {
                    log.error("[HTTP][OUT] method={} uri={} status={} costMs={} query={} params={} body={} remote={}",
                            request.getMethod(), uri, status, costMs, request.getQueryString(),
                            formatParameters(requestToUse), requestBody, request.getRemoteAddr());
                } else if (status >= 400) {
                    log.warn("[HTTP][OUT] method={} uri={} status={} costMs={} query={} params={} body={} remote={}",
                            request.getMethod(), uri, status, costMs, request.getQueryString(),
                            formatParameters(requestToUse), requestBody, request.getRemoteAddr());
                } else {
                    log.info("[HTTP][OUT] method={} uri={} status={} costMs={} params={} body={}",
                            request.getMethod(), uri, status, costMs, formatParameters(requestToUse), requestBody);
                }
            }
        }
    }

    private String formatParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "{}";
        }
        return parameterMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Arrays.toString(maskParameterValues(entry.getKey(), entry.getValue())))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String summarizeRequestBody(ContentCachingRequestWrapper request) {
        if (hasMultipartContentType(request)) {
            return "[multipart body omitted]";
        }
        byte[] body = request.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = request.getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(request.getCharacterEncoding());
        String text = new String(body, charset).trim();
        text = maskSensitiveBody(text);
        if (text.length() <= 2000) {
            return text;
        }
        return text.substring(0, 2000) + "...";
    }

    private String[] maskParameterValues(String key, String[] values) {
        if (!isSensitiveField(key) || values == null) {
            return values;
        }
        String[] masked = new String[values.length];
        Arrays.fill(masked, "***");
        return masked;
    }

    private String maskSensitiveBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode jsonNode = JsonUtil.fromJson(body, JsonNode.class);
            maskSensitiveNode(jsonNode);
            return JsonUtil.toJson(jsonNode);
        } catch (Exception ex) {
            return maskSensitiveText(body);
        }
    }

    private void maskSensitiveNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.properties().forEach(entry -> {
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();
                if (isSensitiveField(fieldName)) {
                    objectNode.put(fieldName, "***");
                } else {
                    maskSensitiveNode(child);
                }
            });
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                maskSensitiveNode(child);
            }
        }
    }

    private String maskSensitiveText(String text) {
        String masked = text;
        for (String field : SENSITIVE_FIELDS) {
            masked = masked.replaceAll("(?i)(\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
            masked = masked.replaceAll("(?i)(" + java.util.regex.Pattern.quote(field) + "\\s*=\\s*)([^&\\s]+)", "$1***");
        }
        return masked;
    }

    private boolean isSensitiveField(String fieldName) {
        return fieldName != null && SENSITIVE_FIELDS.contains(fieldName.trim().toLowerCase());
    }

    private boolean requiresJsonContentType(HttpServletRequest request) {
        boolean writeMethod = HttpMethod.POST.matches(request.getMethod())
                || HttpMethod.PUT.matches(request.getMethod())
                || HttpMethod.PATCH.matches(request.getMethod());
        return writeMethod && hasRequestBody(request);
    }

    private boolean hasRequestBody(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > 0) {
            return true;
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        return transferEncoding != null && !transferEncoding.isBlank();
    }

    private boolean hasJsonContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasMultipartContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasAllowedContentType(HttpServletRequest request) {
        return hasJsonContentType(request) || hasMultipartContentType(request);
    }
}
