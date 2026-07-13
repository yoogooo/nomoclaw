package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;

public interface Tool {

    String name();

    ToolResult execute(ToolRequest request);
}

