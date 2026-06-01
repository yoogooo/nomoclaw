package ai.nomoclaw.bot.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateServiceTests {

    private final PromptTemplateService service = new PromptTemplateService(new DefaultResourceLoader());

    @Test
    void loadsChineseTemplateByLocale() {
        String content = service.load("tips", "system", Locale.SIMPLIFIED_CHINESE, "fallback");

        assertTrue(content.contains("锦囊评估与提炼器"));
    }

    @Test
    void fallsBackToChineseTemplateForUnsupportedLocale() {
        String content = service.load("tips", "system", Locale.FRENCH, "fallback");

        assertTrue(content.contains("锦囊评估与提炼器"));
    }

    @Test
    void fallsBackToProvidedTextWhenTemplateIsMissing() {
        String content = service.load("missing", "system", Locale.ENGLISH, "fallback");

        assertEquals("fallback", content);
    }

    @Test
    void rejectsUnsafePathParts() {
        assertThrows(IllegalArgumentException.class,
                () -> service.load("../tips", "system", Locale.ENGLISH, "fallback"));
    }
}
