package com.suraj.netbeans.codex;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;

/** A bounded, paginated view of shared local Codex chats. */
final class ChatHistoryDialog extends JDialog {
    private final CodexHistory history;
    private final BooleanSupplier connected;
    private final Consumer<CodexHistory.Conversation> resumed;
    private final DefaultListModel<CodexHistory.Entry> entries = new DefaultListModel<>();
    private final JList<CodexHistory.Entry> list = new JList<>(entries);
    private final JTextField search = new JTextField(24);
    private final JButton refresh = new JButton("Search / Refresh");
    private final JButton more = new JButton("Load older chats");
    private final JButton resume = new JButton("Resume chat");
    private final JButton close = new JButton("Close");
    private final JLabel status = new JLabel("Loading chats…");
    private final Set<String> ids = new HashSet<>();
    private String cursor;
    private String query = "";
    private boolean loading;

    ChatHistoryDialog(Window owner, CodexHistory history, BooleanSupplier connected,
            Consumer<CodexHistory.Conversation> resumed) {
        super(owner, "Codex chat history", ModalityType.APPLICATION_MODAL);
        this.history = history;
        this.connected = connected;
        this.resumed = resumed;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Local Codex chats · all projects"), BorderLayout.NORTH);
        search.getAccessibleContext().setAccessibleName("Search chat titles");
        search.setToolTipText("Search saved titles (case-sensitive)");
        top.add(search, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Saved Codex chats");
        list.setCellRenderer(new DefaultListCellRenderer() {
            private final DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());
            @Override public Component getListCellRendererComponent(JList<?> target, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(target, value, index, selected, focused);
                CodexHistory.Entry entry = (CodexHistory.Entry) value;
                // Render plain text: saved titles must never be interpreted as Swing HTML.
                cell.putClientProperty("html.disable", true);
                String title = entry.title().length() > 100 ? entry.title().substring(0, 100) + "…" : entry.title();
                cell.setText(title + "  ·  " + date.format(Instant.ofEpochSecond(entry.updatedAt())));
                cell.setToolTipText(entry.cwd() + " · " + entry.id());
                cell.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
                return cell;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        content.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(6, 8));
        bottom.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(more);
        buttons.add(close);
        buttons.add(resume);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setMinimumSize(new Dimension(480, 300));
        setSize(700, 440);
        setLocationRelativeTo(owner);
        list.addListSelectionListener(event -> updateButtons());
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && resume.isEnabled()) resume.doClick();
            }
        });
        refresh.addActionListener(event -> load(true));
        search.addActionListener(event -> load(true));
        more.addActionListener(event -> load(false));
        resume.addActionListener(event -> resumeSelected());
        close.addActionListener(event -> dispose());
        getRootPane().setDefaultButton(resume);
        load(true);
    }

    private void updateButtons() {
        refresh.setEnabled(!loading);
        search.setEnabled(!loading);
        more.setEnabled(!loading && cursor != null);
        resume.setEnabled(!loading && list.getSelectedValue() != null);
        list.setEnabled(!loading);
    }

    private void load(boolean reset) {
        if (loading) return;
        if (reset) {
            entries.clear();
            ids.clear();
            cursor = null;
            query = search.getText().strip();
        }
        String next = cursor;
        loading = true;
        updateButtons();
        status.setText("Loading chats…");
        new SwingWorker<CodexHistory.Page, Void>() {
            @Override protected CodexHistory.Page doInBackground() throws Exception {
                if (!connected.getAsBoolean()) throw new java.io.IOException("Connection lost. Reconnect and open History again.");
                return history.list(next, query);
            }
            @Override protected void done() {
                if (!isDisplayable()) return;
                loading = false;
                try {
                    CodexHistory.Page page = get();
                    if (!connected.getAsBoolean()) throw new java.io.IOException("Connection lost. Reconnect and open History again.");
                    for (CodexHistory.Entry entry : page.entries()) if (ids.add(entry.id())) entries.addElement(entry);
                    cursor = page.cursor();
                    status.setText(entries.isEmpty() ? "No saved chats found in this Codex home."
                            : entries.size() + " chats · select one to resume (hover for project folder)");
                } catch (Exception error) {
                    status.setText("Could not load chats. Search / Refresh to retry.");
                    status.setToolTipText(CodexHistory.rootCause(error).getMessage());
                }
                updateButtons();
            }
        }.execute();
    }

    private void resumeSelected() {
        CodexHistory.Entry entry = list.getSelectedValue();
        if (entry == null || loading) return;
        loading = true;
        updateButtons();
        status.setText("Loading conversation…");
        new SwingWorker<CodexHistory.Conversation, Void>() {
            @Override protected CodexHistory.Conversation doInBackground() throws Exception {
                if (!connected.getAsBoolean()) throw new java.io.IOException("Connection lost.");
                return history.resume(entry.id());
            }
            @Override protected void done() {
                if (!isDisplayable()) return;
                loading = false;
                try {
                    CodexHistory.Conversation conversation = get();
                    if (!connected.getAsBoolean()) throw new java.io.IOException("Connection lost. Reconnect and try again.");
                    resumed.accept(conversation);
                    dispose();
                } catch (Exception error) {
                    status.setText("Could not resume this chat. Select another or retry.");
                    JOptionPane.showMessageDialog(ChatHistoryDialog.this,
                            error.getCause() == null ? error.getMessage() : error.getCause().getMessage(), "Cannot resume chat", JOptionPane.ERROR_MESSAGE);
                    updateButtons();
                }
            }
        }.execute();
    }
}
