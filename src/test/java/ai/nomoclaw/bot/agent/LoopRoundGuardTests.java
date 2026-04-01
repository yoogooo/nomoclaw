package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.orchestrator.LoopRoundGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopRoundGuardTests {

    @Test
    void roundFourShouldAllowReplanToRoundFive() {
        LoopRoundGuard guard = new LoopRoundGuard();
        assertTrue(guard.canReplan(4, 5));
    }

    @Test
    void roundFiveShouldStopFurtherReplan() {
        LoopRoundGuard guard = new LoopRoundGuard();
        assertFalse(guard.canReplan(5, 5));
    }
}

