package ai.nomoclaw.bot.planner;

import java.util.List;

public record PlannerResult(
        String status,
        List<PlannerOutputItem> output
) {
    public static PlannerResult answer(String answer) {
        return new PlannerResult("completed", List.of(
                new PlannerOutputItem("message", answer, null, null, null, null)
        ));
    }

    public static PlannerResult requiresAction(List<PlannerOutputItem> output) {
        return new PlannerResult("continue", output);
    }

    public boolean completed() {
        return "completed".equalsIgnoreCase(status);
    }

    public String answer() {
        return output.stream()
                .filter(item -> "message".equalsIgnoreCase(item.type()))
                .map(PlannerOutputItem::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("");
    }
}
