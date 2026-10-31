package com.suraj.netbeans.codex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.text.PlainDocument;

public final class MultiRepositoryChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createDirectories(Path.of("target", "multi-repository-check").toAbsolutePath());
        Path backend = Files.createDirectories(root.resolve("backend"));
        Path frontend = Files.createDirectories(root.resolve("frontend"));
        Path unrelated = Files.createDirectories(root.resolve("frontend-other"));
        Files.createDirectories(backend.resolve(".git"));
        Path module = Files.createDirectories(backend.resolve("modules/api"));
        check(WorkspaceFolders.repositoryRoot(module).equals(backend.toRealPath()), "Use the full Git repository rather than a nested IDE module");
        List<Path> folders = new ArrayList<>();
        WorkspaceFolders.add(folders, backend);
        WorkspaceFolders.add(folders, frontend);
        WorkspaceFolders.add(folders, frontend.resolve("."));
        check(folders.size() == 2, "Deduplicate equivalent folder paths");
        Path api = backend.resolve("Api.txt");
        Path ui = frontend.resolve("View.txt");
        Path outside = unrelated.resolve("Secret.txt");
        Files.writeString(api, "backend code");
        Files.writeString(ui, "frontend code");
        Files.writeString(outside, "unrelated");
        check(WorkspaceFolders.containing(ui, folders).equals(frontend.toRealPath()), "Frontend belongs to its own root");
        try { WorkspaceFolders.containing(outside, folders); throw new AssertionError("Do not include every sibling project or path prefix match"); }
        catch (IOException expected) { }
        PlainDocument document = new PlainDocument();
        document.insertString(0, "UNSAVED frontend buffer", null);
        EditorContext editor = EditorContext.capture(ui, document, 2);
        String prompt = editor.appendTo(WorkspaceFolders.appendContext("Update the API contract", folders), folders);
        check(prompt.contains(backend.toString().replace("\\", "\\\\")) && prompt.contains("UNSAVED frontend buffer"), "Include both roots and unsaved active frontend text");
        check(prompt.contains("Repository/project folder: " + frontend.toRealPath()), "Identify the right root when names overlap across repositories");

        SwingUtilities.invokeAndWait(() -> {
            try {
                CodexTopComponent component = new CodexTopComponent();
                var field = CodexTopComponent.class.getDeclaredField("workspaceFolders");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") List<Path> configured = (List<Path>) field.get(component);
                configured.add(backend.toRealPath());
                var build = CodexTopComponent.class.getDeclaredMethod("buildPromptWithAttachments", String.class, List.class, List.class);
                build.setAccessible(true);
                String attached = (String) build.invoke(component, "Compare BE and FE", List.of(api.toString(), ui.toString()), List.copyOf(folders));
                check(attached.contains("backend code") && attached.contains("frontend code"), "Attach files from both repositories in one request");
                check(attached.contains(api.toRealPath().toString()) && attached.contains(ui.toRealPath().toString()), "Use absolute attachment paths to avoid ambiguous filenames");
                var thread = CodexTopComponent.class.getDeclaredField("threadId");
                thread.setAccessible(true);
                thread.set(component, "same-chat");
                var include = CodexTopComponent.class.getDeclaredMethod("includeFileProject", Path.class);
                include.setAccessible(true);
                include.invoke(component, ui);
                check(thread.get(component).equals("same-chat"), "Adding another project must not create or switch the chat");
                include.invoke(component, ui);
                check(configured.size() == 2, "Already included active projects are not duplicated");
                component.componentClosed();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        String savedId = "multi-repository-test-" + java.util.UUID.randomUUID();
        try {
            WorkspaceFolders.save(savedId, folders);
            check(WorkspaceFolders.load(savedId, backend.toString()).equals(folders), "Restore both repository folders when resuming the same chat");
        } finally {
            org.openide.util.NbPreferences.forModule(WorkspaceFolders.class).remove("folders." + savedId);
        }
        System.out.println("PASS: backend/frontend workspace roots, Git-root discovery, unsaved buffers, cross-repository attachments, unchanged thread ID, unrelated-folder boundaries");
    }
}
