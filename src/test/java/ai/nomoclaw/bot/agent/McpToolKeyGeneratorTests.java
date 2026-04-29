package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.mcp.McpToolKeyGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolKeyGeneratorTests {

    private final McpToolKeyGenerator generator = new McpToolKeyGenerator();

    @Test
    void shouldGenerateShortReadableKey() {
        String key = generator.generate("browser-automation", "Search Web Pages");

        assertTrue(key.matches("mcp_search_web_pages_[a-f0-9]{8}"));
        assertTrue(key.length() <= 64);
    }

    @Test
    void shouldDistinguishSameToolNameAcrossServers() {
        String first = generator.generate("server-a", "search");
        String second = generator.generate("server-b", "search");

        assertTrue(first.startsWith("mcp_search_"));
        assertTrue(second.startsWith("mcp_search_"));
        assertTrue(!first.equals(second));
    }

    @Test
    void shouldFallbackBlankToolNameToToolSlug() {
        String key = generator.generate("server-a", " ");

        assertEquals("mcp_tool_", key.substring(0, "mcp_tool_".length()));
    }
}
