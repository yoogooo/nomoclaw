package ai.nomoclaw.bot.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@Slf4j
public class PromptTemplateService {

    private static final String PROMPT_ROOT = "classpath:prompts";
    private static final String DEFAULT_LANGUAGE = "zh";

    private final ResourceLoader resourceLoader;

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String namespace, String templateName, Locale locale, String fallback) {
        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedTemplateName = normalizeTemplateName(templateName);
        String language = resolveLanguage(locale);
        String localized = readIfPresent(location(normalizedNamespace, language, normalizedTemplateName));
        if (!localized.isBlank()) {
            return localized;
        }
        String defaultTemplate = readIfPresent(location(normalizedNamespace, DEFAULT_LANGUAGE, normalizedTemplateName));
        if (!defaultTemplate.isBlank()) {
            return defaultTemplate;
        }
        return fallback == null ? "" : fallback;
    }

    private String location(String namespace, String language, String templateName) {
        return "%s/%s/%s/%s.md".formatted(PROMPT_ROOT, namespace, language, templateName);
    }

    private String normalizeNamespace(String namespace) {
        String normalized = normalizePathPart(namespace);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("prompt namespace is required");
        }
        return normalized;
    }

    private String normalizeTemplateName(String templateName) {
        String normalized = normalizePathPart(templateName);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("prompt templateName is required");
        }
        if (normalized.endsWith(".md")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private String normalizePathPart(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("\\")) {
            throw new IllegalArgumentException("invalid prompt path: " + value);
        }
        return normalized;
    }

    private String resolveLanguage(Locale locale) {
        if (locale != null && Locale.CHINESE.getLanguage().equals(locale.getLanguage())) {
            return "zh";
        }
        return DEFAULT_LANGUAGE;
    }

    private String readIfPresent(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return "";
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            log.warn("[PromptTemplate] failed to read location={} err={}", location, ex.toString());
            return "";
        }
    }
}
