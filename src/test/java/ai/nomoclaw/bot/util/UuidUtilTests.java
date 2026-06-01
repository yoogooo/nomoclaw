package ai.nomoclaw.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UuidUtilTests {

    @Test
    void newUuidShouldBe32LowercaseWithoutHyphen() {
        String value = UuidUtil.newUuid();

        assertNotNull(value);
        assertEquals(32, value.length());
        assertFalse(value.contains("-"));
    }

    @Test
    void normalizeShouldHandleCommonInputs() {
        assertEquals("550e8400e29b41d4a716446655440000", UuidUtil.normalize("550e8400-e29b-41d4-a716-446655440000"));
        assertEquals("550e8400e29b41d4a716446655440000", UuidUtil.normalize("550e8400e29b41d4a716446655440000"));
        assertEquals("", UuidUtil.normalize(""));
        assertEquals("   ", UuidUtil.normalize("   "));
        assertEquals(null, UuidUtil.normalize(null));
    }
}
