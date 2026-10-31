package com.suraj.netbeans.codex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;

/** Protocol fixtures for persisted legacy and paginated Codex threads. */
public final class HistoryChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static CompletableFuture<JsonObject> ok(JsonObject value) { return CompletableFuture.completedFuture(value); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createDirectories(Path.of("target", "history-check").toAbsolutePath());
        JsonObject metadata = json("{thread:{id:'saved',status:{type:'idle'},turns:[]}}");
        metadata.getAsJsonObject("thread").addProperty("cwd", root.toString());
        List<String> calls = new ArrayList<>();
        CodexHistory history = new CodexHistory((method, params) -> {
            calls.add(method);
            if (method.equals("thread/list")) {
                check(params.getAsJsonArray("sourceKinds").toString().contains("appServer"), "Include custom/desktop app-server sources");
                if (params.has("cursor")) {
                    check(params.get("cursor").getAsString().equals("next"), "Pass opaque cursor unchanged");
                    check(params.get("searchTerm").getAsString().equals("Find"), "Keep server search across pages");
                    return ok(json("{data:[],nextCursor:null}"));
                }
                return ok(json("{data:[{id:'saved',name:null,preview:'First\\nrequest',cwd:'project',updatedAt:1}],nextCursor:'next'}"));
            }
            if (method.equals("thread/read")) return ok(metadata);
            if (method.equals("thread/resume")) {
                check(params.get("threadId").getAsString().equals("saved"), "Resume exact persisted ID");
                check(params.get("cwd").getAsString().equals(root.toString()), "Keep original project");
                check(params.get("sandbox").getAsString().equals("read-only")
                        && params.get("approvalPolicy").getAsString().equals("never"), "Override permissions inherited from another client");
                return ok(metadata);
            }
            if (method.equals("thread/turns/list")) {
                check(params.get("itemsView").getAsString().equals("full"), "Load full messages rather than summaries");
                check(params.get("sortDirection").getAsString().equals("asc"), "Restore chronological order");
                if (!params.has("cursor")) return ok(json("{data:[{id:'one',items:["
                        + "{type:'userMessage',content:[{type:'text',text:'Original question'},{type:'localImage',path:'ignored'}]},"
                        + "{type:'reasoning',text:'PRIVATE_REASONING'},"
                        + "{type:'commandExecution',aggregatedOutput:'HIDDEN_TOOL_DATA'},"
                        + "{type:'agentMessage',text:'Commentary'}]}],nextCursor:'older'}"));
                return ok(json("{data:[{id:'two',items:[{type:'agentMessage',text:'Final answer'}]}],nextCursor:null}"));
            }
            throw new AssertionError("Unexpected RPC " + method);
        });
        CodexHistory.Page first = history.list(null, "Find");
        check(first.entries().get(0).title().equals("First request"), "Use preview when a title is null");
        check(history.list(first.cursor(), "Find").cursor() == null, "Stop at final history page");
        CodexHistory.Conversation conversation = history.resume("saved");
        check(conversation.messages().size() == 3, "Only display user and agent messages");
        check(conversation.messages().get(0).user() && conversation.messages().get(0).text().contains("[Image attachment]"), "Restore user inputs and attachment placeholders");
        check(conversation.messages().get(2).text().equals("Final answer"), "Fetch all turns across pages");

        CodexHistory legacy = new CodexHistory((method, params) -> {
            if (method.equals("thread/turns/list")) return CompletableFuture.failedFuture(new IOException("list_turns is not supported yet"));
            if (method.equals("thread/read") && params.has("includeTurns")) {
                JsonObject full = metadata.deepCopy();
                full.getAsJsonObject("thread").add("turns", JsonParser.parseString("[{items:[{type:'agentMessage',text:'Legacy answer'}]}]"));
                return ok(full);
            }
            return ok(metadata);
        });
        check(legacy.resume("saved").messages().get(0).text().equals("Legacy answer"), "Fallback for legacy stores without turn pagination");
        int before = calls.size();
        metadata.getAsJsonObject("thread").addProperty("cwd", root.resolve("missing").toString());
        try { history.resume("saved"); throw new AssertionError("Unavailable folders must fail"); }
        catch (IOException expected) { }
        check(calls.size() == before + 1, "Validate folder before mutating or resuming a session");
        metadata.getAsJsonObject("thread").addProperty("cwd", root.toString());
        metadata.getAsJsonObject("thread").getAsJsonObject("status").addProperty("type", "active");
        try { history.resume("saved"); throw new AssertionError("Busy threads must fail"); }
        catch (IOException expected) { }

        SwingUtilities.invokeAndWait(() -> {
            try {
                CodexTopComponent component = new CodexTopComponent();
                var panelField = CodexTopComponent.class.getDeclaredField("chatPanel");
                panelField.setAccessible(true);
                CodexChatPanel panel = (CodexChatPanel) panelField.get(component);
                panel.promptArea.setText("Keep my unsent draft");
                panel.attachmentModel.addElement("old-project-file");
                var restore = CodexTopComponent.class.getDeclaredMethod("restoreConversation", CodexHistory.Conversation.class);
                restore.setAccessible(true);
                restore.invoke(component, conversation);
                check(panel.conversationArea.getText().contains("Original question")
                        && panel.conversationArea.getText().contains("Final answer"), "Render saved transcript in the plugin");
                check(panel.promptArea.getText().equals("Keep my unsent draft"), "Preserve unsent text when switching chats");
                check(panel.attachmentModel.isEmpty(), "Do not leak attachments from the previous project");
                check(panel.isReady() && panel.historyBtn.isEnabled(), "Enable continuation after hydration");
                panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
                check(!panel.historyBtn.isEnabled(), "Prevent switching during a live turn");
                component.componentClosed();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.out.println("PASS: shared history pagination/search, exact thread resume, original cwd, read-only policies, legacy fallback, message hydration, draft preservation, busy-state controls");
    }
}
