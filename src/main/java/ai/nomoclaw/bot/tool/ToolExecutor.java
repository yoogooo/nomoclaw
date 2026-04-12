package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.ToolProgress;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.ToolSpecificationRegistry;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.util.function.Consumer;

@Component
@Slf4j
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final MessageCancellationRegistry cancellationRegistry;
    private final ToolSpecificationRegistry toolSpecificationRegistry;

    public ToolExecutor(ToolRegistry toolRegistry,
                        MessageCancellationRegistry cancellationRegistry,
                        ToolSpecificationRegistry toolSpecificationRegistry) {
        this.toolRegistry = toolRegistry;
        this.cancellationRegistry = cancellationRegistry;
        this.toolSpecificationRegistry = toolSpecificationRegistry;
    }

    public ToolResult execute(String conversationUid,
                              String messageUid,
                              String agentUid,
                              String agentName,
                              java.nio.file.Path agentWorkspacePath,
                              PlanStep step,
                              long timeoutMs,
                              Consumer<ToolProgress> progressReporter) {
        if (cancellationRegistry.isCanceled(messageUid)) {
            return ToolResult.failure("CANCELLED", "message canceled", JsonNodeFactory.instance.objectNode());
        }
        if (!toolSpecificationRegistry.isToolAllowed(agentName, step.toolName())) {
            return ToolResult.failure("UNAUTHORIZED_TOOL", "tool is not enabled for current agent", JsonNodeFactory.instance.objectNode());
        }
        Tool tool = toolRegistry.getRequired(step.toolName());
        log.info("[ToolExecutor] dispatch tool={} conversationUid={} messageUid={} stepUid={} timeoutMs={}",
                tool.name(), conversationUid, messageUid, step.stepUid(), timeoutMs);
        Path workspace = NomoClawPaths.ensureAgentWorkspace(agentWorkspacePath == null
                ? NomoClawPaths.agentWorkspace(agentName)
                : agentWorkspacePath);
        ToolRequest request = new ToolRequest(
                conversationUid,
                messageUid,
                step.stepUid(),
                agentUid,
                agentName,
                workspace,
                NomoClawPaths.agentTmp(workspace),
                NomoClawPaths.agentReport(workspace),
                step.toolArgs(),
                timeoutMs,
                progressReporter
        );
        return tool.execute(request);
    }
}
