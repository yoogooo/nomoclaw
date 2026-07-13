package ai.nomoclaw.bot.util;

import ai.nomoclaw.bot.config.I18nConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStepI18nMessagesTests {

    private final MessageSource messageSource = new I18nConfig().messageSource();
    private final LocalizedMessages localizedMessages = new LocalizedMessages(messageSource);

    @AfterEach
    void cleanupLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldResolveStepDisplayMessageInChinese() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        String text = localizedMessages.get("agent.step.display.command.running.withCommand", "echo hello");

        assertEquals("正在执行命令: echo hello", text);
    }

    @Test
    void shouldResolveStepDisplayMessageInEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        String text = localizedMessages.get("agent.step.display.command.running.withCommand", "echo hello");

        assertEquals("Running command: echo hello", text);
    }

    @Test
    void shouldResolveRunSummaryWithArguments() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        String text = localizedMessages.get("agent.run.summary.running", 2, 5);

        assertTrue(text.contains("2/5"));
        assertEquals("Running, completed 2/5 steps", text);
    }
}

