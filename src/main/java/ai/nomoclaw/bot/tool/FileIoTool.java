package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class FileIoTool implements Tool {

    private final FileTool delegate;

    public FileIoTool(FileTool delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return "file_io_tool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        return delegate.execute(request);
    }
}
