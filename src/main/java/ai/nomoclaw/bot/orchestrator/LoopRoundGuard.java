package ai.nomoclaw.bot.orchestrator;

import org.springframework.stereotype.Component;

@Component
public class LoopRoundGuard {

    public boolean canReplan(int currentRound, int maxRounds) {
        return currentRound < maxRounds;
    }
}

