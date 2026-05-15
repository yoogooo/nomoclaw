package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.model.PlanStep;

import java.util.List;

public record RoundPlanningResult(boolean completed, String answer, List<PlanStep> steps) {

    public static RoundPlanningResult completed(String answer) {
        return new RoundPlanningResult(true, answer, List.of());
    }

    public static RoundPlanningResult requiresAction(List<PlanStep> steps) {
        return new RoundPlanningResult(false, "", steps);
    }
}
