package com.suraj.netbeans.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Uses the shared Codex store through its protocol, never edits rollout files. */
final class CodexHistory {
    record Entry(String id, String title, String cwd, long updatedAt) { }
    record Page(List<Entry> entries, String cursor) { }
    record Message(boolean user, String text) { }
    record Conversation(String id, String cwd, List<Message> messages) { }

    private final BiFunction<String, JsonObject, CompletableFuture<JsonObject>> request;

    CodexHistory(BiFunction<String, JsonObject, CompletableFuture<JsonObject>> request) {
        this.request = request;
    }

    Page list(String cursor, String search) throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("limit", 50);
        params.addProperty("sortKey", "updated_at");
        params.addProperty("sortDirection", "desc");
        // Include desktop/custom app-server clients as well as CLI and VS Code.
        JsonArray sources = new JsonArray();
        for (String source : List.of("cli", "vscode", "appServer", "unknown")) sources.add(source);
        params.add("sourceKinds", sources);
        params.add("modelProviders", new JsonArray());
        if (cursor != null) params.addProperty("cursor", cursor);
        if (!search.isBlank()) params.addProperty("searchTerm", search);
        JsonObject result = request.apply("thread/list", params).get();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement value : array(result, "data")) {
            JsonObject thread = value.getAsJsonObject();
            String title = string(thread, "name");
            if (title.isBlank()) title = string(thread, "preview");
            if (title.isBlank()) title = "Untitled chat";
            title = title.replaceAll("\\s+", " ").strip();
            entries.add(new Entry(string(thread, "id"), title, string(thread, "cwd"),
                    thread.has("updatedAt") ? thread.get("updatedAt").getAsLong() : 0));
        }
        return new Page(List.copyOf(entries), nullableString(result, "nextCursor"));
    }

    Conversation resume(String id) throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("threadId", id);
        JsonObject thread = request.apply("thread/read", params).get().getAsJsonObject("thread");
        if (!id.equals(string(thread, "id"))) throw new IOException("Codex returned a different chat.");
        String cwd = string(thread, "cwd");
        if (cwd.isBlank() || !Path.of(cwd).isAbsolute() || !Files.isDirectory(Path.of(cwd))) {
            throw new IOException("This chat's project folder is unavailable on this computer: " + cwd);
        }
        if (thread.has("status") && thread.get("status").isJsonObject()
                && "active".equals(string(thread.getAsJsonObject("status"), "type"))) {
            throw new IOException("This chat is still running. Wait for it to finish before resuming.");
        }
        params.addProperty("cwd", cwd);
        params.addProperty("approvalPolicy", "never");
        params.addProperty("sandbox", "read-only");
        params.addProperty("excludeTurns", true);
        try {
            request.apply("thread/resume", params).get();
        } catch (Exception error) {
            if (String.valueOf(rootCause(error).getMessage()).contains("already has an active writer")) {
                throw new IOException("This chat is still open in another Codex client. That client must release it before it can be resumed here. Switching chats in the desktop app may keep its writer lock.", error);
            }
            throw error;
        }
        List<Message> messages = new ArrayList<>();
        String cursor = null;
        Set<String> cursors = new HashSet<>();
        Set<String> turns = new HashSet<>();
        do {
            JsonObject pageParams = new JsonObject();
            pageParams.addProperty("threadId", id);
            pageParams.addProperty("limit", 50);
            pageParams.addProperty("sortDirection", "asc");
            pageParams.addProperty("itemsView", "full");
            if (cursor != null) pageParams.addProperty("cursor", cursor);
            JsonObject page;
            try {
                page = request.apply("thread/turns/list", pageParams).get();
            } catch (Exception error) {
                Throwable cause = rootCause(error);
                String message = String.valueOf(cause.getMessage());
                if (!message.contains("-32601") && !message.contains("not supported")) throw error;
                JsonObject read = new JsonObject();
                read.addProperty("threadId", id);
                read.addProperty("includeTurns", true);
                JsonObject legacy = request.apply("thread/read", read).get().getAsJsonObject("thread");
                messages.clear();
                for (JsonElement turn : array(legacy, "turns")) appendTurn(turn.getAsJsonObject(), messages);
                break;
            }
            for (JsonElement value : array(page, "data")) {
                JsonObject turn = value.getAsJsonObject();
                if (turns.add(string(turn, "id"))) appendTurn(turn, messages);
            }
            cursor = nullableString(page, "nextCursor");
            if (cursor != null && !cursors.add(cursor)) throw new IOException("Codex repeated a history cursor. Refresh and try again.");
        } while (cursor != null);
        return new Conversation(id, cwd, List.copyOf(messages));
    }

    private static void appendTurn(JsonObject turn, List<Message> messages) {
        for (JsonElement value : array(turn, "items")) {
            JsonObject item = value.getAsJsonObject();
            String type = string(item, "type");
            if ("agentMessage".equals(type)) {
                String text = string(item, "text");
                if (!text.isBlank()) messages.add(new Message(false, text));
            } else if ("userMessage".equals(type)) {
                StringBuilder text = new StringBuilder();
                for (JsonElement part : array(item, "content")) {
                    JsonObject content = part.getAsJsonObject();
                    String kind = string(content, "type");
                    String partText = switch (kind) {
                        case "text" -> string(content, "text");
                        case "image", "localImage" -> "[Image attachment]";
                        default -> "[" + kind + " attachment]";
                    };
                    if (text.length() > 0) text.append('\n');
                    text.append(partText);
                }
                if (text.length() > 0) messages.add(new Message(true, text.toString()));
            }
            // Reasoning, tool payloads and instructions are not conversation messages.
        }
        if (turn.has("error") && turn.get("error").isJsonObject()) {
            messages.add(new Message(false, "Error: " + string(turn.getAsJsonObject("error"), "message")));
        }
    }

    static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
    private static String nullableString(JsonObject object, String key) {
        String value = string(object, key);
        return value.isEmpty() ? null : value;
    }
    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
