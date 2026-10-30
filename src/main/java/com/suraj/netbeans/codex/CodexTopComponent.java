/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/NetBeansModuleDevelopment-files/templateTopComponent637.java to edit this template
 */
package com.suraj.netbeans.codex;

import com.google.gson.JsonObject;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.awt.HtmlBrowser;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle;
import com.google.gson.JsonArray;
import javax.swing.JFileChooser;
import java.io.File;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.Utilities;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.beans.PropertyChangeListener;
import javax.swing.JEditorPane;
import javax.swing.DefaultListModel;
import org.openide.filesystems.FileObject;
import org.openide.cookies.EditorCookie;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//com.suraj.netbeans.codex//Codex//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "CodexTopComponent",
        iconBase = "com/suraj/netbeans/codex/codex.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "properties", openAtStartup = false)
@ActionID(category = "Window", id = "com.suraj.netbeans.codex.CodexTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CodexAction",
        preferredID = "CodexTopComponent"
)
public final class CodexTopComponent extends TopComponent {

    private CodexClient codexClient;
    private volatile int connectionGeneration;
    private volatile String threadId;
    private String projectDirectory;
    private boolean turnInProgress;
    private boolean sendAfterSignIn;
    private Project lastEditorProject;
    private DataObject lastEditorFile;
    private EditorContext queuedEditorContext;
    private final Set<String> streamedMessages = new HashSet<>();
    private String lastAgentMessageId;
    private boolean receivedAgentText;
    private final Map<String, String> activeWork = new LinkedHashMap<>();
    private final PropertyChangeListener editorActivationListener = event -> rememberEditorProject();

    private final Lookup.Result<DataObject> editorSelection
            = Utilities.actionsGlobalContext().lookupResult(DataObject.class);

    private final LookupListener editorSelectionListener
            = event -> rememberEditorProject();

    private DefaultListModel<String> attachmentModel;

    public CodexTopComponent() {
        initComponents();
        initializeChat();
        setName(NbBundle.getMessage(CodexTopComponent.class, "CTL_CodexTopComponent"));
        setToolTipText(NbBundle.getMessage(CodexTopComponent.class, "HINT_CodexTopComponent"));
        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.SIGNED_OUT);
        attachmentsList.setModel(attachmentModel);
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        chatPanel = new com.suraj.netbeans.codex.CodexChatPanel();

        setMinimumSize(new java.awt.Dimension(280, 260));
        setPreferredSize(new java.awt.Dimension(390, 680));
        setLayout(new java.awt.BorderLayout());
        add(chatPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void initializeChat() {
        attachmentModel = chatPanel.attachmentModel;
        conversationArea = chatPanel.conversationArea;
        promptArea = chatPanel.promptArea;
        sendBtn = chatPanel.sendBtn;
        checkRuntimeButton = chatPanel.checkRuntimeButton;
        removeAttachmentBtn = chatPanel.removeAttachmentBtn;
        attachmentsList = chatPanel.attachmentsList;
        sendBtn.addActionListener(this::sendBtnActionPerformed);
        checkRuntimeButton.addActionListener(this::checkRuntimeButtonActionPerformed);
        removeAttachmentBtn.addActionListener(this::removeAttachmentBtnActionPerformed);
        chatPanel.attachBtn.addActionListener(event -> chooseAttachments());
        chatPanel.newChatBtn.addActionListener(event -> {
            if (turnInProgress) {
                return;
            }
            threadId = null;
            sendAfterSignIn = false;
            queuedEditorContext = null;
            projectDirectory = null;
            conversationArea.setText("");
            promptArea.setText("");
            attachmentModel.clear();
            chatPanel.setProjectDirectory(null);
            promptArea.requestFocusInWindow();
        });
    }

    private void chooseAttachments() {
        if (turnInProgress) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Attach source files");
        chooser.setMultiSelectionEnabled(true);
        if (projectDirectory != null) {
            chooser.setCurrentDirectory(new File(projectDirectory));
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<DataObject> selected = new ArrayList<>();
        for (File file : chooser.getSelectedFiles()) {
            FileObject object = FileUtil.toFileObject(FileUtil.normalizeFile(file));
            if (object != null) {
                try {
                    selected.add(DataObject.find(object));
                } catch (IOException error) {
                    conversationArea.append("Cannot attach " + file.getName() + ": "
                            + error.getMessage() + "\n");
                }
            }
        }
        addAttachments(selected);
    }
    private void sendBtnActionPerformed(java.awt.event.ActionEvent evt) {
        final String prompt = promptArea.getText().trim();

        if (prompt.isEmpty() || turnInProgress) {
            return;
        }

        if (!chatPanel.isReady()) {
            if (chatPanel.canSignIn()) {
                queuedEditorContext = captureEditorContext();
                sendAfterSignIn = true;
                checkRuntimeButton.doClick();
            }
            return;
        }

        final EditorContext editorContext = queuedEditorContext != null
                ? queuedEditorContext : captureEditorContext();
        queuedEditorContext = null;

        if (projectDirectory == null) {
            projectDirectory = detectProjectDirectory();

            if (projectDirectory != null) {
                conversationArea.append(
                        "Project: " + projectDirectory + "\n\n");
            }
        }

        if (projectDirectory == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a project folder for Codex");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            projectDirectory = chooser.getSelectedFile()
                    .toPath().toAbsolutePath().normalize().toString();
        }

        chatPanel.setProjectDirectory(projectDirectory);

        final CodexClient client = codexClient;
        final int generation = connectionGeneration;
        final String directory = projectDirectory;

        final List<String> attachedPaths = new ArrayList<>();

        for (int i = 0; i < attachmentModel.size(); i++) {
            attachedPaths.add(attachmentModel.getElementAt(i));
        }

        turnInProgress = true;
        streamedMessages.clear();
        activeWork.clear();
        lastAgentMessageId = null;
        receivedAgentText = false;
        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
        promptArea.setText("");

        conversationArea.append("You: " + prompt + "\n\nCodex: ");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String completePrompt = buildPromptWithAttachments(
                        prompt, attachedPaths, directory);
                if (editorContext != null) {
                    completePrompt = editorContext.appendTo(completePrompt, directory);
                }

                if (threadId == null) {
                    JsonObject threadParams = new JsonObject();
                    threadParams.addProperty("cwd", directory);
                    threadParams.addProperty("approvalPolicy", "never");
                    threadParams.addProperty("sandbox", "read-only");

                    JsonObject response
                            = client.request("thread/start", threadParams).get();

                    if (generation != connectionGeneration) {
                        return null;
                    }

                    threadId = response.getAsJsonObject("thread")
                            .get("id").getAsString();
                }

                JsonObject textInput = new JsonObject();
                textInput.addProperty("type", "text");
                textInput.addProperty("text", completePrompt);

                JsonArray input = new JsonArray();
                input.add(textInput);

                JsonObject sandboxPolicy = new JsonObject();
                sandboxPolicy.addProperty("type", "readOnly");

                JsonObject turnParams = new JsonObject();
                turnParams.addProperty("threadId", threadId);
                turnParams.add("input", input);
                turnParams.addProperty("approvalPolicy", "never");
                turnParams.add("sandboxPolicy", sandboxPolicy);

                client.request("turn/start", turnParams).get();
                return null;
            }

            @Override
            protected void done() {
                if (generation != connectionGeneration) {
                    return;
                }

                try {
                    get();
                    // Streaming continues through the notification callback.
                    // Send is enabled when turn/completed arrives.
                } catch (Exception e) {
                    Throwable cause = e;

                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }

                    conversationArea.append(
                            "\nError: " + cause.getMessage() + "\n\n");

                    turnInProgress = false;
                    chatPanel.setConnectionState(codexClient != null
                            ? CodexChatPanel.ConnectionState.READY
                            : CodexChatPanel.ConnectionState.SIGNED_OUT);
                }
            }
        }.execute();
    }

    private void checkRuntimeButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (!chatPanel.canSignIn()) {
            return;
        }
        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.CONNECTING);
        conversationArea.append("Connecting to bundled codex...\n");
        threadId = null;
        turnInProgress = false;

        int generation = ++connectionGeneration;

        CodexClient previous = codexClient;

        if (previous != null) {
            previous.close();
        }

        CodexClient client = new CodexClient(message -> SwingUtilities.invokeLater(() -> {
            if (generation != connectionGeneration) {
                return;
            }

            handleChatNotification(message);

            if ("account/login/completed".equals(message.get("method").getAsString())) {
                JsonObject params = message.getAsJsonObject("params");

                if (params.get("success").getAsBoolean()) {
                    conversationArea.append("Signed in with ChatGPT.\n");
                    finishSignIn(generation);
                } else {
                    String error = params.has("error") && !params.get("error").isJsonNull() ? params.get("error").getAsString() : "Login was not completed.";

                    conversationArea.append("Login failed: " + error + "\n");
                    sendAfterSignIn = false;
                    queuedEditorContext = null;
                    chatPanel.setConnectionState(CodexChatPanel.ConnectionState.SIGNED_OUT);
                }
            }
        }), error -> SwingUtilities.invokeLater(() -> {
            if (generation != connectionGeneration) {
                return;
            }

            // Ignore queued notifications and worker results from this connection.
            connectionGeneration++;
            sendAfterSignIn = false;
            queuedEditorContext = null;
            codexClient = null;
            threadId = null;
            turnInProgress = false;
            chatPanel.setConnectionState(CodexChatPanel.ConnectionState.SIGNED_OUT);
            conversationArea.append("\nConnection lost: " + error.getMessage()
                    + "\nSign in with ChatGPT to reconnect.\n\n");
        }));

        codexClient = client;

        new javax.swing.SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                client.start();

                JsonObject accountParams = new JsonObject();
                accountParams.addProperty("refreshToken", false);

                JsonObject state = client.request(
                        "account/read", accountParams).get();

                if (state.has("account") && !state.get("account").isJsonNull()) {
                    JsonObject account = state.getAsJsonObject("account");

                    if ("chatgpt".equals(account.get("type").getAsString())) {
                        return null; // Existing ChatGPT session.
                    }
                }

                JsonObject loginParams = new JsonObject();
                loginParams.addProperty("type", "chatgpt");

                JsonObject login = client.request(
                        "account/login/start", loginParams).get();

                return login.get("authUrl").getAsString();
            }

            @Override
            protected void done() {
                if (generation != connectionGeneration) {
                    client.close();
                    return;
                }

                try {
                    String authUrl = get();

                    if (authUrl == null) {
                        conversationArea.append("Already signed in with ChatGPT.\n");
                        finishSignIn(generation);
                    } else {
                        if (chatPanel.isReady() || turnInProgress || chatPanel.canSignIn()) {
                            return;
                        }
                        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.AWAITING_LOGIN);
                        HtmlBrowser.URLDisplayer.getDefault()
                                .showURL(java.net.URI.create(authUrl).toURL());

                        conversationArea.append(
                                "Complete sign-in in your browser. "
                                + "Keep this panel open.\n");
                    }
                } catch (Exception exception) {
                    client.close();
                    codexClient = null;
                    sendAfterSignIn = false;
                    queuedEditorContext = null;
                    chatPanel.setConnectionState(CodexChatPanel.ConnectionState.SIGNED_OUT);

                    Throwable cause = exception;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }

                    conversationArea.append("Error: " + cause.getMessage() + "\n");
                }
            }
        }.execute();
    }

    private void finishSignIn(int generation) {
        if (generation != connectionGeneration || chatPanel.isReady() || turnInProgress) {
            return;
        }
        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.READY);
        if (sendAfterSignIn) {
            // Consume the intent before sending: login can complete through both
            // the account response and a notification, but the turn starts once.
            sendAfterSignIn = false;
            sendBtnActionPerformed(null);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.suraj.netbeans.codex.CodexChatPanel chatPanel;
    // End of variables declaration//GEN-END:variables
    private javax.swing.JList<String> attachmentsList;
    private javax.swing.JButton checkRuntimeButton;
    private CodexChatPanel.Transcript conversationArea;
    private javax.swing.JTextArea promptArea;
    private javax.swing.JButton removeAttachmentBtn;
    private javax.swing.JButton sendBtn;
    @Override
    public void componentOpened() {
        editorSelection.addLookupListener(editorSelectionListener);
        TopComponent.getRegistry().addPropertyChangeListener(editorActivationListener);
        rememberEditorProject();
    }

    @Override
    public void componentClosed() {
        editorSelection.removeLookupListener(editorSelectionListener);
        TopComponent.getRegistry().removePropertyChangeListener(editorActivationListener);
        lastEditorProject = null;
        lastEditorFile = null;
        queuedEditorContext = null;
        chatPanel.setActiveFile(null);
        connectionGeneration++;
        sendAfterSignIn = false;
        threadId = null;
        projectDirectory = null;
        chatPanel.setProjectDirectory(null);
        turnInProgress = false;
        chatPanel.setConnectionState(CodexChatPanel.ConnectionState.SIGNED_OUT);

        if (codexClient != null) {
            codexClient.close();
            codexClient = null;
        }

    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    private void handleChatNotification(JsonObject message) {
        if (!message.has("method") || !message.has("params")) {
            return;
        }

        String method = message.get("method").getAsString();
        JsonObject params = message.getAsJsonObject("params");

        if (threadId == null
                || !params.has("threadId")
                || !threadId.equals(params.get("threadId").getAsString())) {
            return;
        }

        if (!turnInProgress) return;

        if ("item/agentMessage/delta".equals(method)) {
            appendAgentMessage(string(params, "itemId"), string(params, "delta"));
            chatPanel.setActivity("Responding");
        } else if ("item/started".equals(method) || "item/completed".equals(method)) {
            JsonObject item = params.getAsJsonObject("item");
            if (item == null) return;
            String id = string(item, "id");
            String type = string(item, "type");
            if ("item/started".equals(method)) {
                String activity = switch (type) {
                    case "commandExecution" -> "Running a command";
                    case "mcpToolCall", "dynamicToolCall" -> "Using a tool";
                    case "webSearch" -> "Searching";
                    case "contextCompaction" -> "Compacting context";
                    case "plan" -> "Planning";
                    case "agentMessage" -> "Responding";
                    default -> "Thinking";
                };
                activeWork.put(id, activity);
                chatPanel.setActivity(activity);
            } else {
                if ("agentMessage".equals(type) && !streamedMessages.contains(id)) {
                    appendAgentMessage(id, string(item, "text"));
                }
                activeWork.remove(id);
                chatPanel.setActivity(activeWork.isEmpty() ? "Thinking"
                        : new ArrayList<>(activeWork.values()).get(activeWork.size() - 1));
            }
        } else if (method.startsWith("item/reasoning/") || "turn/plan/updated".equals(method)) {
            chatPanel.setActivity("Thinking");
        } else if ("error".equals(method)) {
            JsonObject error = params.getAsJsonObject("error");
            conversationArea.append("\n" + (error == null ? "Request error" : string(error, "message")) + "\n");
            chatPanel.setActivity(params.has("willRetry") && params.get("willRetry").getAsBoolean()
                    ? "Reconnecting" : "Finishing");
        } else if ("turn/completed".equals(method)) {
            JsonObject turn = params.getAsJsonObject("turn");

            if (turn != null
                    && turn.has("error")
                    && !turn.get("error").isJsonNull()) {
                JsonObject error = turn.getAsJsonObject("error");

                conversationArea.append("\nError: "
                        + (error.has("message")
                        ? error.get("message").getAsString()
                        : error.toString()));
            } else if (!receivedAgentText) {
                conversationArea.append("No text response was returned. You can try the request again.");
            }

            conversationArea.append("\n\n");
            turnInProgress = false;
            chatPanel.setConnectionState(codexClient != null
                    ? CodexChatPanel.ConnectionState.READY
                    : CodexChatPanel.ConnectionState.SIGNED_OUT);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private void appendAgentMessage(String itemId, String text) {
        if (text.isEmpty()) return;
        boolean newMessage = lastAgentMessageId != null && !lastAgentMessageId.equals(itemId);
        lastAgentMessageId = itemId;
        streamedMessages.add(itemId);
        receivedAgentText = true;
        conversationArea.appendAgentText(text, newMessage);
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void rememberEditorProject() {
        TopComponent active = TopComponent.getRegistry().getActivated();
        DataObject editor = editorFileIn(active);
        if (editor != null) {
            lastEditorFile = editor;
        } else if (editorPane(lastEditorFile) == null) {
            lastEditorFile = null;
            // The editor stays selected in its mode when focus moves to Codex.
            for (Mode mode : WindowManager.getDefault().getModes()) {
                if (WindowManager.getDefault().isEditorMode(mode)) {
                    editor = editorFileIn(mode.getSelectedTopComponent());
                    if (editor != null) {
                        lastEditorFile = editor;
                        break;
                    }
                }
            }
        }
        File local = lastEditorFile == null ? null : FileUtil.toFile(lastEditorFile.getPrimaryFile());
        chatPanel.setActiveFile(local == null ? null : local.toPath());
        if (lastEditorFile != null) {
            Project owner = FileOwnerQuery.getOwner(lastEditorFile.getPrimaryFile());
            if (owner != null) {
                lastEditorProject = owner;
                return;
            }
        }
        for (DataObject selected : editorSelection.allInstances()) {
            Project owner = FileOwnerQuery.getOwner(selected.getPrimaryFile());

            if (owner != null) {
                lastEditorProject = owner;
                return;
            }
        }
    }

    private static JEditorPane editorPane(DataObject file) {
        if (file == null || !file.isValid()) return null;
        EditorCookie cookie = file.getLookup().lookup(EditorCookie.class);
        JEditorPane[] panes = cookie == null ? null : cookie.getOpenedPanes();
        return panes == null || panes.length == 0 ? null : panes[0];
    }

    private DataObject editorFileIn(TopComponent component) {
        if (component == null || component == this) return null;
        DataObject file = component.getLookup().lookup(DataObject.class);
        JEditorPane pane = editorPane(file);
        if (pane != null && SwingUtilities.isDescendingFrom(pane, component)) return file;
        for (DataObject candidate : editorSelection.allInstances()) {
            pane = editorPane(candidate);
            if (pane != null && SwingUtilities.isDescendingFrom(pane, component)) return candidate;
        }
        return null;
    }

    private EditorContext captureEditorContext() {
        rememberEditorProject();
        JEditorPane pane = editorPane(lastEditorFile);
        File file = lastEditorFile == null ? null : FileUtil.toFile(lastEditorFile.getPrimaryFile());
        return pane == null || file == null ? null
                : EditorContext.capture(file.toPath(), pane.getDocument(), pane.getCaretPosition());
    }

    private String detectProjectDirectory() {
        Project project = Utilities.actionsGlobalContext()
                .lookup(Project.class);

        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();

        if (project == null && lastEditorProject != null) {
            // Only reuse the remembered project if it is still open.
            for (Project openProject : openProjects) {
                if (openProject.getProjectDirectory().equals(
                        lastEditorProject.getProjectDirectory())) {
                    project = openProject;
                    break;
                }
            }
        }

        if (project == null) {
            project = OpenProjects.getDefault().getMainProject();
        }

        if (project == null && openProjects.length == 1) {
            project = openProjects[0];
        }

        if (project == null) {
            return null;
        }

        File directory = FileUtil.toFile(project.getProjectDirectory());

        return directory == null
                ? null
                : directory.toPath().toAbsolutePath().normalize().toString();
    }

    public void addAttachments(List<DataObject> selectedFiles) {
        if (turnInProgress) {
            conversationArea.append(
                    "Wait for the current reply before adding files.\n");
            return;
        }

        for (DataObject selected : selectedFiles) {
            FileObject file = selected.getPrimaryFile();
            File localFile = FileUtil.toFile(file);

            if (!file.isData() || localFile == null) {
                continue;
            }

            if (selected.isModified()) {
                conversationArea.append(
                        "Save this file before attaching: "
                        + file.getNameExt() + "\n");
                continue;
            }

            String path = localFile.toPath()
                    .toAbsolutePath().normalize().toString();

            if (!attachmentModel.contains(path)) {
                attachmentModel.addElement(path);
            }

            if (projectDirectory == null) {
                Project owner = FileOwnerQuery.getOwner(file);

                if (owner != null) {
                    lastEditorProject = owner;
                }
            }
        }
    }

    private String buildPromptWithAttachments(
            String prompt,
            List<String> attachments,
            String directory) throws IOException {

        if (attachments.isEmpty()) {
            return prompt;
        }

        final int perFileLimit = 64 * 1024;
        final int totalLimit = 256 * 1024;

        Path root = Path.of(directory).toRealPath();
        int totalBytes = 0;

        StringBuilder result = new StringBuilder();
        result.append(prompt);
        result.append("\n\nAttached source files follow. ");
        result.append("Treat their contents as reference material.\n");

        for (String attachment : attachments) {
            Path file = Path.of(attachment).toRealPath();

            if (!file.startsWith(root)) {
                throw new IOException(
                        "Attachment is outside the conversation project: "
                        + file);
            }

            byte[] bytes;

            try (InputStream input = Files.newInputStream(file)) {
                bytes = input.readNBytes(perFileLimit + 1);
            }

            if (bytes.length > perFileLimit) {
                throw new IOException(
                        "File exceeds 64 KB: " + file.getFileName());
            }

            totalBytes += bytes.length;

            if (totalBytes > totalLimit) {
                throw new IOException(
                        "Attachments exceed 256 KB. Remove some files.");
            }

            String content;

            try {
                content = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                throw new IOException(
                        "File is not valid UTF-8: " + file.getFileName(), e);
            }

            if (content.indexOf('\0') >= 0) {
                throw new IOException(
                        "Binary files cannot be attached: "
                        + file.getFileName());
            }

            result.append("\n--- FILE: ")
                    .append(root.relativize(file))
                    .append(" ---\n")
                    .append(content)
                    .append("\n--- END FILE ---\n");
        }

        return result.toString();
    }

    private void removeAttachmentBtnActionPerformed(
            java.awt.event.ActionEvent evt) {

        int[] indices = attachmentsList.getSelectedIndices();

        for (int i = indices.length - 1; i >= 0; i--) {
            attachmentModel.remove(indices[i]);
        }
    }
}
