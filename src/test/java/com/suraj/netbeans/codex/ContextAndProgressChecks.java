package com.suraj.netbeans.codex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.PlainDocument;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.MultiDataObject;
import org.openide.nodes.CookieSet;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

/** Standalone regression checks; never contacts the Codex service. */
public final class ContextAndProgressChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static void notify(CodexTopComponent component, String json) throws Exception {
        Method method = CodexTopComponent.class.getDeclaredMethod("handleChatNotification", JsonObject.class);
        method.setAccessible(true);
        method.invoke(component, JsonParser.parseString(json).getAsJsonObject());
    }
    private static void editorChecks(Path root) throws Exception {
        Path source = root.resolve("ApiController.txt");
        Files.writeString(source, "old disk contents");
        PlainDocument document = new PlainDocument();
        document.insertString(0, "<?php\n// UNSAVED editor code\nclass ApiController {}", null);
        EditorContext context = EditorContext.capture(source, document, 10);
        document.insertString(document.getLength(), "\nnewer edit", null);
        String prompt = context.appendTo("Check this file", root.toString());
        check(prompt.contains(source.toRealPath().toString()), "Prompt must identify the exact editor file");
        check(prompt.contains("UNSAVED editor code") && !prompt.contains("old disk contents"), "Use buffer contents, not stale disk text");
        check(!prompt.contains("newer edit"), "Queued context must be an immutable snapshot");
        check(prompt.contains("Cursor line: 2"), "Include the cursor line");
        try {
            context.appendTo("Check", root.resolve("other").toString());
            throw new AssertionError("An editor outside the chat project must not be included");
        } catch (java.io.IOException expected) { }
        PlainDocument large = new PlainDocument();
        large.insertString(0, "x".repeat(70 * 1024), null);
        check(EditorContext.capture(source, large, 0).content() == null, "Oversized buffers must use path context only");

        // Use a real DataObject with an editor cookie to exercise editor detection.
        DataObject data = DataObject.find(FileUtil.createMemoryFileSystem().getRoot().createData("ApiController.txt"));
        JEditorPane pane = new JEditorPane();
        pane.setDocument(document);
        EditorCookie cookie = (EditorCookie) Proxy.newProxyInstance(EditorCookie.class.getClassLoader(),
                new Class<?>[]{EditorCookie.class}, (object, method, args) -> switch (method.getName()) {
                    case "getOpenedPanes" -> new JEditorPane[]{pane};
                    case "getDocument", "openDocument" -> document;
                    case "isModified" -> true;
                    case "toString" -> "Test editor cookie";
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    default -> null;
                });
        Method cookies = MultiDataObject.class.getDeclaredMethod("getCookieSet");
        cookies.setAccessible(true);
        ((CookieSet) cookies.invoke(data)).assign(EditorCookie.class, cookie);
        TopComponent editor = new TopComponent(Lookups.fixed(data));
        editor.setLayout(new BorderLayout());
        editor.add(pane);
        CodexTopComponent component = new CodexTopComponent();
        Method find = CodexTopComponent.class.getDeclaredMethod("editorFileIn", TopComponent.class);
        find.setAccessible(true);
        check(find.invoke(component, editor) == data, "Detect the active editor's DataObject");
        check(find.invoke(component, component) == null, "The chat composer must not become the editor context");
        component.componentClosed();
    }
    private static void progressChecks() throws Exception {
        CodexTopComponent component = new CodexTopComponent();
        CodexChatPanel panel = (CodexChatPanel) field(component, "chatPanel");
        set(component, "threadId", "thread-test");
        set(component, "turnInProgress", true);
        panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
        panel.conversationArea.append("You: Check this file\n\nCodex: ");
        panel.conversationArea.showThinking();
        notify(component, "{method:'item/agentMessage/delta',params:{threadId:'thread-test',itemId:'comment',delta:'I will check it.'}}");
        check(((JPanel) field(panel, "activityRow")).isVisible(), "Progress must remain after the first commentary");
        panel.setSize(390, 900);
        layout(panel);
        layout(panel);
        JPanel activity = (JPanel) field(panel, "activityRow");
        check(activity.getParent() == panel.conversationArea.getParent(), "Activity must scroll with the transcript");
        int responseEnd = panel.conversationArea.modelToView2D(panel.conversationArea.getDocument().getLength()).getBounds().y;
        check(activity.getY() > responseEnd && activity.getY() - responseEnd < 65,
                "Thinking must sit immediately below the reply, not at the bottom of the sidebar");
        notify(component, "{method:'item/completed',params:{threadId:'thread-test',item:{type:'agentMessage',id:'comment',text:'I will check it.'}}}");
        notify(component, "{method:'item/started',params:{threadId:'thread-test',item:{type:'commandExecution',id:'command'}}}");
        check(((JLabel) field(panel, "activity")).getText().contains("Running"), "Display command activity");
        notify(component, "{method:'item/reasoning/textDelta',params:{threadId:'thread-test',delta:'PRIVATE_REASONING'}}");
        check(!panel.conversationArea.getText().contains("PRIVATE_REASONING"), "Do not display raw reasoning");
        notify(component, "{method:'item/agentMessage/delta',params:{threadId:'other-thread',itemId:'other',delta:'WRONG_THREAD'}}");
        notify(component, "{method:'item/agentMessage/delta',params:{threadId:'thread-test',itemId:'final',delta:'Here is the review.'}}");
        notify(component, "{method:'item/completed',params:{threadId:'thread-test',item:{type:'agentMessage',id:'final',text:'Here is the review.'}}}");
        String text = panel.conversationArea.getText().replace("\r\n", "\n");
        check(text.endsWith("I will check it.\n\nHere is the review."), "Separate commentary and final answer without duplication");
        check(!text.contains("WRONG_THREAD"), "Ignore activity for another chat");
        notify(component, "{method:'turn/completed',params:{threadId:'thread-test',turn:{status:'completed',error:null}}}");
        check(!((JPanel) field(panel, "activityRow")).isVisible(), "Stop progress at actual turn completion");
        check(!(boolean) field(component, "turnInProgress"), "Mark the turn complete");
        component.componentClosed();

        component = new CodexTopComponent();
        panel = (CodexChatPanel) field(component, "chatPanel");
        set(component, "threadId", "thread-test");
        set(component, "turnInProgress", true);
        panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
        notify(component, "{method:'item/completed',params:{threadId:'thread-test',item:{type:'agentMessage',id:'only',text:'Completed-only reply'}}}");
        check(panel.conversationArea.getText().contains("Completed-only reply"), "Render replies delivered without deltas");
        component.componentClosed();
    }
    private static void scrollingChecks() throws Exception {
        CodexChatPanel panel = new CodexChatPanel();
        panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
        panel.conversationArea.appendAgentText(("LongIdentifier".repeat(15) + "\n").repeat(40), false);
        JPanel activity = (JPanel) field(panel, "activityRow");
        for (int width : new int[]{390, 280, 600}) {
            panel.setSize(width, 900);
            layout(panel);
            layout(panel);
            int end = panel.conversationArea.getDocument().getLength();
            java.awt.Rectangle lastLine = panel.conversationArea.modelToView2D(end).getBounds();
            check(lastLine.y + lastLine.height <= panel.conversationArea.getHeight(), "Long replies must not be clipped");
            check(activity.getY() >= lastLine.y && activity.getY() - lastLine.y < 65, "Activity follows the end of long replies");
            activity.scrollRectToVisible(new java.awt.Rectangle(0, 0, activity.getWidth(), activity.getHeight()));
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, activity);
            java.awt.Rectangle visible = SwingUtilities.convertRectangle(activity.getParent(), activity.getBounds(), viewport);
            check(visible.y >= 0 && visible.y + visible.height <= viewport.getHeight(), "Latest activity must be scrollable into view");
            check(viewport.getViewPosition().x == 0, "Wrapping must not shift horizontally");
        }
        panel.setConnectionState(CodexChatPanel.ConnectionState.READY);
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of("target", "context-progress-check").toAbsolutePath();
        Files.createDirectories(root.resolve("other"));
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { editorChecks(root); progressChecks(); scrollingChecks(); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
        } catch (java.lang.reflect.InvocationTargetException failure) {
            failure.getCause().printStackTrace();
            System.exit(1);
        }
        System.out.println("PASS: active editor detection, unsaved snapshot, path boundaries, persistent activity, command events, message separation, completed-only replies");
    }
}
