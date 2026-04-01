package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class BrowserControlTool implements Tool {

    private final BrowserTool delegate;

    public BrowserControlTool(BrowserTool delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return "browser_control_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        return delegate.execute(request);
    }
}
