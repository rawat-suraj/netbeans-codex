package com.suraj.netbeans.codex;

import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

public final class AccessModeChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        List<Path> folders = List.of(Path.of("target/backend").toAbsolutePath(), Path.of("target/frontend").toAbsolutePath());
        var read = AccessMode.READ_ONLY.sandboxPolicy(folders);
        check(read.get("type").getAsString().equals("readOnly") && !read.has("writableRoots"), "Read-only must not grant write access");
        var edit = AccessMode.EDIT_WORKSPACE.sandboxPolicy(folders);
        check(edit.get("type").getAsString().equals("workspaceWrite"), "Use the workspace write sandbox");
        check(edit.getAsJsonArray("writableRoots").size() == 2, "Grant access to backend and frontend roots");
        check(!edit.get("networkAccess").getAsBoolean(), "Editing mode must not silently enable unrestricted networking");
        SwingUtilities.invokeAndWait(() -> {
            CodexChatPanel panel = new CodexChatPanel();
            check(panel.accessMode() == AccessMode.READ_ONLY, "Default to read-only");
            panel.setConnectionState(CodexChatPanel.ConnectionState.READY);
            check(panel.mode.isEnabled(), "Allow the user to select editing mode while idle");
            panel.mode.setSelectedItem(AccessMode.EDIT_WORKSPACE);
            check(panel.accessMode() == AccessMode.EDIT_WORKSPACE, "The dropdown must change the selected mode");
            panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
            check(!panel.mode.isEnabled(), "Do not change permissions during a running turn");
            panel.setConnectionState(CodexChatPanel.ConnectionState.READY);
            panel.resetAccessMode();
            check(panel.accessMode() == AccessMode.READ_ONLY, "Reset permissions for another conversation");
        });
        System.out.println("PASS: selectable read/edit modes, backend/frontend writable roots, safe defaults, busy-state mode locking");
    }
}
