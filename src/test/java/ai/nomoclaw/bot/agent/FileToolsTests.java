package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.tool.CreateFileTool;
import ai.nomoclaw.bot.tool.EditFileTool;
import ai.nomoclaw.bot.tool.ListFileTool;
import ai.nomoclaw.bot.tool.ReadFileTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolsTests {

    @TempDir
    Path tempDir;

    @Test
    void createReadAndEditShouldWork() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        CreateFileTool createTool = new CreateFileTool();
        ObjectNode createArgs = JsonNodeFactory.instance.objectNode();
        createArgs.put("path", "demo.txt");
        createArgs.put("mode", "create_or_truncate");
        createArgs.put("content", "line1\nline2");
        ToolResult createResult = createTool.execute(request(workspace, createArgs));
        assertTrue(createResult.success());

        ReadFileTool readTool = new ReadFileTool();
        ObjectNode readArgs = JsonNodeFactory.instance.objectNode();
        readArgs.put("path", "demo.txt");
        ToolResult readResult = readTool.execute(request(workspace, readArgs));
        assertTrue(readResult.success());
        assertTrue(readResult.output().contains("1: line1"));

        EditFileTool editTool = new EditFileTool();
        ObjectNode editArgs = JsonNodeFactory.instance.objectNode();
        editArgs.put("path", "demo.txt");
        editArgs.put("oldText", "line2");
        editArgs.put("newText", "line2-updated");
        ToolResult editResult = editTool.execute(request(workspace, editArgs));
        assertTrue(editResult.success());
        assertEquals("line1\nline2-updated", Files.readString(workspace.resolve("demo.txt")));
    }

    @Test
    void listShouldReturnEntries() throws Exception {
        Path workspace = tempDir.resolve("workspace-list");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("a.txt"), "a");
        Files.writeString(workspace.resolve("b.txt"), "b");

        ListFileTool listTool = new ListFileTool();
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("path", ".");
        ToolResult result = listTool.execute(request(workspace, args));
        assertTrue(result.success());
        assertTrue(result.output().contains("a.txt"));
        assertTrue(result.output().contains("b.txt"));
    }

    @Test
    void createShouldRejectInvalidMode() {
        Path workspace = tempDir.resolve("workspace-invalid");
        CreateFileTool createTool = new CreateFileTool();
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("path", "demo.txt");
        args.put("mode", "unknown");
        args.put("content", "x");
        ToolResult result = createTool.execute(request(workspace, args));
        assertFalse(result.success());
        assertEquals("INVALID_ARGS", result.errorCode());
    }

    private ToolRequest request(Path workspace, ObjectNode args) {
        return new ToolRequest(
                "c1",
                "m1",
                "s1",
                "a1",
                "default_agent",
                workspace,
                workspace.resolve("tmp"),
                workspace.resolve("report"),
                args,
                10_000,
                null
        );
    }
}
