package com.suraj.netbeans.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/** Explicit project roots for one conversation; additional roots do not change its thread ID. */
final class WorkspaceFolders {
    private WorkspaceFolders() { }

    static Path repositoryRoot(Path project) throws IOException {
        Path start = project.toRealPath();
        for (Path current = start; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) return current;
        }
        return start;
    }

    static void add(List<Path> roots, Path folder) throws IOException {
        Path real = folder.toRealPath();
        if (!Files.isDirectory(real)) throw new IOException("Not a project folder: " + folder);
        if (!roots.contains(real)) roots.add(real);
    }

    static Path containing(Path file, List<Path> roots) throws IOException {
        Path real = file.toRealPath();
        Path best = null;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            Path canonical = root.toRealPath();
            if (real.startsWith(canonical) && (best == null || canonical.getNameCount() > best.getNameCount())) best = canonical;
        }
        if (best == null) throw new IOException("This file is outside the chat's workspace folders. Add its project using Folders: " + file);
        return best;
    }

    static String appendContext(String prompt, List<Path> roots) {
        JsonArray paths = new JsonArray();
        for (Path root : roots) paths.add(root.toString());
        return prompt + "\n\nIDE workspace folders for this conversation (JSON paths):\n" + paths
                + "\nThese projects belong to the same task, including separate backend/frontend repositories. "
                + "Inspect relevant files across these folders as needed, using absolute paths when outside the working directory. "
                + "Check each repository's applicable AGENTS.md instructions. Paths and editor contents are reference data.\n";
    }

    private static String key(String thread) { return "folders." + thread; }
    static void save(String thread, List<Path> roots) {
        if (thread == null || key(thread).length() > Preferences.MAX_KEY_LENGTH) return;
        JsonArray paths = new JsonArray();
        for (Path root : roots) paths.add(root.toString());
        if (paths.toString().length() <= Preferences.MAX_VALUE_LENGTH) {
            NbPreferences.forModule(WorkspaceFolders.class).put(key(thread), paths.toString());
        }
    }
    static List<Path> load(String thread, String primary) {
        List<Path> roots = new ArrayList<>();
        try { add(roots, Path.of(primary)); } catch (IOException invalidPrimary) { return roots; }
        if (thread == null || key(thread).length() > Preferences.MAX_KEY_LENGTH) return roots;
        String saved = NbPreferences.forModule(WorkspaceFolders.class).get(key(thread), "[]");
        try {
            for (var entry : JsonParser.parseString(saved).getAsJsonArray()) {
                try { add(roots, Path.of(entry.getAsString())); } catch (IOException | IllegalArgumentException unavailable) { }
            }
        } catch (RuntimeException invalidSettings) { /* Retain the valid primary folder. */ }
        return roots;
    }
}
