package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.ToolProgress;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.mcp.McpApplicationService;
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
    private final McpApplicationService mcpApplicationService;

    public ToolExecutor(ToolRegistry toolRegistry,
                        MessageCancellationRegistry cancellationRegistry,
                        ToolSpecificationRegistry toolSpecificationRegistry,
                        McpApplicationService mcpApplicationService) {
        this.toolRegistry = toolRegistry;
        this.cancellationRegistry = cancellationRegistry;
        this.toolSpecificationRegistry = toolSpecificationRegistry;
        this.mcpApplicationService = mcpApplicationService;
    }

    public ToolResult execute(String conversationUid,
                              String messageUid,
                              String agentUid,
                              String agentName,
                              Path agentWorkspacePath,
                              Path tmpDirectory,
                              Path reportDirectory,
                              PlanStep step,
                              long timeoutMs,
                              Consumer<ToolProgress> progressReporter) {
        if (cancellationRegistry.isCanceled(messageUid)) {
            return ToolResult.failure("CANCELLED", "message canceled", JsonNodeFactory.instance.objectNode());
        }
        if (!toolSpecificationRegistry.isToolAllowed(agentName, step.toolName())) {
            return ToolResult.failure("UNAUTHORIZED_TOOL", "tool is not enabled for current agent", JsonNodeFactory.instance.objectNode());
        }
        log.info("[ToolExecutor] dispatch tool={} conversationUid={} messageUid={} stepUid={} timeoutMs={}",
                step.toolName(), conversationUid, messageUid, step.stepUid(), timeoutMs);
        Path workspace = NomoClawPaths.ensureAgentWorkspace(agentWorkspacePath == null
                ? NomoClawPaths.agentWorkspace(agentName)
                : agentWorkspacePath);
        Path tmpDir = ensureDirectory(tmpDirectory == null ? NomoClawPaths.agentTmp(workspace) : tmpDirectory, "agent tmp");
        Path reportDir = ensureDirectory(reportDirectory == null ? NomoClawPaths.agentReport(workspace) : reportDirectory, "agent report");
        ToolRequest request = new ToolRequest(
                conversationUid,
                messageUid,
                step.stepUid(),
                agentUid,
                agentName,
                workspace,
                tmpDir,
                reportDir,
                step.toolArgs(),
                timeoutMs,
                progressReporter
        );
        if (toolSpecificationRegistry.isMcpTool(step.toolName())) {
            return mcpApplicationService.execute(step.toolName(), request);
        }
        Tool tool = toolRegistry.getRequired(step.toolName());
        return tool.execute(request);
    }

    private Path ensureDirectory(Path path, String label) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize " + label + ": " + path, ex);
        }
    }
}
