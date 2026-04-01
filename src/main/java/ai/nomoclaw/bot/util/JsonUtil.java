package ai.nomoclaw.bot.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * JSON 工具类，基于 Jackson3 JsonMapper，统一模块加载、空值处理与错误兜底。
 */
public final class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
                            .withContentInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private JsonUtil() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to serialize object", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize json", e);
        }
    }

    public static <T> Optional<T> fromJsonQuietly(String json, Class<T> type) {
        try {
            return Optional.ofNullable(MAPPER.readValue(json, type));
        } catch (Exception e) {
            log.warn("Failed to deserialize json quietly: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static <T> Optional<T> fromJsonQuietly(String json, TypeReference<T> typeRef) {
        try {
            return Optional.ofNullable(MAPPER.readValue(json, typeRef));
        } catch (Exception e) {
            log.warn("Failed to deserialize json quietly: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
