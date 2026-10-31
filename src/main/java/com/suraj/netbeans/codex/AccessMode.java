package com.suraj.netbeans.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

enum AccessMode {
    READ_ONLY("Chat · Read only", "Inspect code without modifying files", "read-only"),
    EDIT_WORKSPACE("Agent · Edit workspace", "Edit files and run commands in this chat's workspace folders", "workspace-write");

    private final String label;
    final String tooltip;
    final String threadSandbox;

    AccessMode(String label, String tooltip, String threadSandbox) {
        this.label = label;
        this.tooltip = tooltip;
        this.threadSandbox = threadSandbox;
    }

    JsonObject sandboxPolicy(List<Path> folders) {
        JsonObject policy = new JsonObject();
        policy.addProperty("type", this == READ_ONLY ? "readOnly" : "workspaceWrite");
        if (this == EDIT_WORKSPACE) {
            JsonArray roots = new JsonArray();
            for (Path folder : folders) roots.add(folder.toAbsolutePath().normalize().toString());
            policy.add("writableRoots", roots);
            policy.addProperty("networkAccess", false);
        }
        return policy;
    }

    @Override public String toString() { return label; }
}
