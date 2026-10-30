package com.suraj.netbeans.codex;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.*;

/** Hand-built Swing chat surface; independent of the app-server connection. */
public final class CodexChatPanel extends JPanel {
    final DefaultListModel<String> attachmentModel;
    enum ConnectionState { SIGNED_OUT, CONNECTING, AWAITING_LOGIN, READY, BUSY }
    private ConnectionState connectionState = ConnectionState.SIGNED_OUT;
    private final JLabel mode = new JLabel("Chat · Read only");
    final Transcript conversationArea = new Transcript();
    final JTextArea promptArea = new JTextArea(3, 1) {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty()) {
                graphics.setColor(muted);
                graphics.setFont(getFont());
                graphics.drawString("Ask Codex about your code…", 0, getFontMetrics(getFont()).getAscent() + 2);
            }
        }
    };
    final JButton sendBtn = iconButton("send", "Send message (Enter)");
    final JButton checkRuntimeButton = new JButton("Sign in with ChatGPT");
    final JButton removeAttachmentBtn = iconButton("remove", "Remove selected attachments");
    final JButton attachBtn = iconButton("add", "Attach source files");
    final JButton newChatBtn = iconButton("new", "New chat");
    final JList<String> attachmentsList;
    private final Color background = color("Panel.background", new Color(0x1f1f1f));
    private final boolean dark = background.getRed() + background.getGreen() + background.getBlue() < 384;
    private final Color foreground = dark ? new Color(0xe6e6e6) : new Color(0x242424);
    private final Color muted = dark ? new Color(0xa4a4a4) : new Color(0x626262);
    private final Color surface = dark ? new Color(0x292929) : Color.WHITE;
    private final Color line = dark ? new Color(0x454545) : new Color(0xd6d6d6);
    private final JLabel status = new JLabel("Sign in to get started");
    private final JLabel project = new JLabel("Current project");
    private final JLabel activeFile = new JLabel("No active editor file");
    private final JLabel activity = new JLabel();
    private final JPanel activityRow = new JPanel(new BorderLayout());
    private String activityText = "Thinking";
    private int activityTick;
    private long turnStartedNanos;
    private final Timer activityTimer = new Timer(450, event -> {
        activityTick = (activityTick + 1) % 4;
        long seconds = Math.max(0, (System.nanoTime() - turnStartedNanos) / 1_000_000_000L);
        activity.setText(activityText + ".".repeat(activityTick + 1) + "  ·  " + seconds + "s");
    });
    private final JPanel cards = new JPanel(new CardLayout());

    /** JavaBean constructor used by the NetBeans form designer. */
    public CodexChatPanel() {
        this(new DefaultListModel<>());
    }

    CodexChatPanel(DefaultListModel<String> attachments) {
        super(new BorderLayout());
        attachmentModel = attachments;
        setBackground(background);
        setPreferredSize(new Dimension(390, 680));
        setMinimumSize(new Dimension(280, 260));
        Font font = UIManager.getFont("Label.font");
        if (font == null) font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        font = font.deriveFont(13f);
        setFont(font);
        for (JButton button : new JButton[]{attachBtn, newChatBtn, removeAttachmentBtn}) {
            button.setForeground(foreground);
        }

        JPanel header = panel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, line),
                BorderFactory.createEmptyBorder(10, 14, 10, 12)));
        JLabel title = label("Codex", 14, true);
        title.setIcon(new CodexLogo(22));
        title.setIconTextGap(9);
        header.add(title, BorderLayout.WEST);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        styleButton(checkRuntimeButton);
        checkRuntimeButton.setFont(font.deriveFont(11f));
        JLabel version = label("v1", 10, false);
        version.setForeground(muted);
        actions.add(version);
        actions.add(newChatBtn);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        conversationArea.setEditable(false);
        conversationArea.setBackground(background);
        conversationArea.setForeground(foreground);
        conversationArea.setFont(font);
        conversationArea.setMargin(new Insets(18, 18, 18, 18));
        conversationArea.getAccessibleContext().setAccessibleName("Conversation with Codex");
        cards.setOpaque(false);
        cards.add(welcome(), "welcome");
        activityRow.setBackground(background);
        activityRow.setBorder(BorderFactory.createEmptyBorder(6, 18, 10, 18));
        activity.setForeground(muted);
        activity.setFont(font.deriveFont(13f));
        activity.getAccessibleContext().setAccessibleName("Codex activity");
        activityRow.add(activity, BorderLayout.CENTER);
        activityRow.setVisible(false);
        cards.add(scroll(new ConversationStack()), "conversation");
        add(cards, BorderLayout.CENTER);
        conversationArea.getDocument().addDocumentListener(onChange(() ->
                ((CardLayout) cards.getLayout()).show(cards,
                        conversationArea.getDocument().getLength() == 0 ? "welcome" : "conversation")));

        JPanel bottom = panel(new BorderLayout(0, 9));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        project.setFont(font.deriveFont(11f));
        project.setForeground(muted);
        project.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
        JPanel context = panel(new BorderLayout(0, 5));
        context.add(project, BorderLayout.NORTH);
        activeFile.setFont(font.deriveFont(11f));
        activeFile.setForeground(muted);
        activeFile.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
        context.add(activeFile, BorderLayout.SOUTH);
        bottom.add(context, BorderLayout.NORTH);

        JPanel composer = new RoundedPanel(surface, new BorderLayout(0, 8), new RoundedBorder(line, 14));
        promptArea.setFont(font);
        promptArea.setBackground(surface);
        promptArea.setForeground(foreground);
        promptArea.setCaretColor(foreground);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBorder(BorderFactory.createEmptyBorder());
        promptArea.getAccessibleContext().setAccessibleName("Message Codex");
        JScrollPane promptScroll = scroll(promptArea);
        promptScroll.setPreferredSize(new Dimension(0, 76));
        composer.add(promptScroll, BorderLayout.CENTER);
        promptArea.getDocument().addDocumentListener(onChange(() -> {
            promptArea.repaint();
            updateStatus();
        }));
        promptArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        promptArea.getActionMap().put("send-message", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { sendBtn.doClick(); }
        });
        promptArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), DefaultEditorKit.insertBreakAction);

        attachmentsList = new JList<>(attachments);
        attachmentsList.setBackground(surface);
        attachmentsList.setForeground(foreground);
        attachmentsList.setVisibleRowCount(2);
        attachmentsList.setFont(font.deriveFont(11f));
        attachmentsList.setSelectionBackground(dark ? new Color(0x414141) : new Color(0xe8e8e8));
        attachmentsList.setSelectionForeground(foreground);
        attachmentsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                cell.setText(Path.of(value.toString()).getFileName().toString());
                cell.setToolTipText(value.toString());
                cell.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return cell;
            }
        });
        attachmentsList.getAccessibleContext().setAccessibleName("Attached source files");
        JPanel attachmentRow = new JPanel(new BorderLayout(6, 0));
        attachmentRow.setOpaque(false);
        JScrollPane attachmentScroll = scroll(attachmentsList);
        attachmentScroll.setPreferredSize(new Dimension(0, 50));
        attachmentRow.add(attachmentScroll, BorderLayout.CENTER);
        attachmentRow.add(removeAttachmentBtn, BorderLayout.EAST);
        attachmentRow.setVisible(!attachments.isEmpty());
        composer.add(attachmentRow, BorderLayout.NORTH);
        attachments.addListDataListener(new ListDataListener() {
            private void changed() { attachmentRow.setVisible(!attachments.isEmpty()); revalidate(); repaint(); }
            @Override public void intervalAdded(ListDataEvent e) { changed(); }
            @Override public void intervalRemoved(ListDataEvent e) { changed(); }
            @Override public void contentsChanged(ListDataEvent e) { changed(); }
        });
        attachmentsList.addListSelectionListener(e -> removeAttachmentBtn.setEnabled(!attachmentsList.isSelectionEmpty()));
        removeAttachmentBtn.setEnabled(false);

        JPanel tools = panel(new BorderLayout());
        tools.setOpaque(false);
        JPanel leftTools = panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftTools.setOpaque(false);
        leftTools.add(attachBtn);
        mode.setFont(font.deriveFont(11f));
        mode.setForeground(muted);
        mode.setToolTipText("Codex can read your project. File changes are disabled.");
        leftTools.add(mode);
        leftTools.add(checkRuntimeButton);
        tools.add(leftTools, BorderLayout.WEST);
        sendBtn.setOpaque(true);
        sendBtn.setContentAreaFilled(true);
        sendBtn.setBackground(dark ? new Color(0xe6e6e6) : new Color(0x292929));
        sendBtn.setForeground(dark ? new Color(0x242424) : Color.WHITE);
        tools.add(sendBtn, BorderLayout.EAST);
        composer.add(tools, BorderLayout.SOUTH);
        bottom.add(composer, BorderLayout.CENTER);
        JPanel footer = panel(new BorderLayout(6, 0));
        status.setFont(font.deriveFont(10f));
        status.setForeground(muted);
        footer.add(status, BorderLayout.WEST);
        JLabel hint = label("Shift + Enter for new line", 10, false);
        hint.setForeground(muted);
        footer.add(hint, BorderLayout.EAST);
        bottom.add(footer, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        updateStatus();
    }

    void setProjectDirectory(String directory) {
        Path path = directory == null ? null : Path.of(directory);
        project.setText(path == null ? "Current project"
                : path.getFileName() == null ? path.toString() : path.getFileName().toString());
        project.setToolTipText(directory == null ? "Project is detected when you send a message" : directory);
    }

    void setConnectionState(ConnectionState state) {
        if (state == ConnectionState.BUSY && connectionState != state) {
            turnStartedNanos = System.nanoTime();
            setActivity("Thinking");
        }
        connectionState = state;
        activityRow.setVisible(state == ConnectionState.BUSY);
        if (state == ConnectionState.BUSY) activityTimer.start();
        else activityTimer.stop();
        activityRow.getParent().revalidate();
        if (state != ConnectionState.BUSY) {
            conversationArea.clearThinking();
        }
        updateStatus();
    }

    void setActiveFile(Path path) {
        activeFile.setText(path == null ? "No active editor file" : "Active file: " + path.getFileName());
        activeFile.setToolTipText(path == null ? "Focus a source editor to include its current contents" : path.toString());
    }

    void setActivity(String text) {
        activityText = text;
        activityTick = 0;
        activity.setText(text + "…");
        SwingUtilities.invokeLater(() -> {
            if (connectionState == ConnectionState.BUSY && activityRow.isShowing()) {
                activityRow.scrollRectToVisible(new Rectangle(0, 0, activityRow.getWidth(), activityRow.getHeight()));
            }
        });
    }

    @Override public void removeNotify() {
        activityTimer.stop();
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (connectionState == ConnectionState.BUSY) activityTimer.start();
    }

    boolean isReady() { return connectionState == ConnectionState.READY; }
    boolean canSignIn() { return connectionState == ConnectionState.SIGNED_OUT; }

    private void updateStatus() {
        boolean signedOut = canSignIn();
        boolean working = connectionState == ConnectionState.BUSY;
        boolean signedIn = isReady() || working;
        status.setText(switch (connectionState) {
            case SIGNED_OUT -> "Sign in to send messages";
            case CONNECTING -> "Connecting…";
            case AWAITING_LOGIN -> "Complete sign-in in your browser";
            case READY -> "Connected";
            case BUSY -> "Codex is working…";
        });
        checkRuntimeButton.setText(switch (connectionState) {
            case CONNECTING -> "Connecting…";
            case AWAITING_LOGIN -> "Waiting for sign-in…";
            default -> "Sign in with ChatGPT";
        });
        checkRuntimeButton.setVisible(!signedIn);
        checkRuntimeButton.setEnabled(signedOut);
        mode.setVisible(signedIn);
        sendBtn.setEnabled((signedOut || isReady()) && !promptArea.getText().isBlank());
        String sendDescription = signedOut ? "Sign in with ChatGPT and send this message"
                : working ? "Wait for the current reply"
                : signedIn ? "Send message (Enter)" : "Complete sign-in to send";
        sendBtn.setToolTipText(sendDescription);
        sendBtn.getAccessibleContext().setAccessibleName(sendDescription);
        newChatBtn.setEnabled(signedOut || isReady());
        attachBtn.setEnabled(!working);
    }

    private JPanel welcome() {
        JPanel outer = panel(new GridBagLayout());
        JPanel content = panel(new GridLayout(0, 1, 0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(20, 18, 40, 18));
        JLabel mark = new JLabel(new CodexLogo(42));
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(mark);
        JLabel heading = label("Let's build something", 20, true);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(heading);
        JLabel subtitle = label("Ask questions. Explore your code.", 12, false);
        subtitle.setForeground(muted);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(subtitle);
        for (String suggestion : new String[]{"Explain this project", "Find bugs in my code", "Review the attached files"}) {
            JButton button = new JButton(suggestion + "  ↗");
            styleButton(button);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(line, 8),
                    BorderFactory.createEmptyBorder(0, 2, 0, 2)));
            button.addActionListener(e -> { promptArea.setText(suggestion); promptArea.requestFocusInWindow(); });
            content.add(button);
        }
        outer.add(content);
        return outer;
    }

    private JPanel panel(LayoutManager layout) {
        JPanel result = new JPanel(layout);
        result.setBackground(background);
        return result;
    }
    private JLabel label(String text, int size, boolean bold) {
        JLabel result = new JLabel(text);
        result.setFont(getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        result.setForeground(foreground);
        return result;
    }
    private void styleButton(JButton button) {
        button.setForeground(foreground);
        button.setFont(getFont());
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private static JButton iconButton(String kind, String description) {
        JButton button = new JButton(new LineIcon(kind));
        button.setPreferredSize(new Dimension(30, 30));
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    private JScrollPane scroll(JComponent view) {
        JScrollPane result = new JScrollPane(view);
        result.setBorder(BorderFactory.createEmptyBorder());
        result.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        result.getVerticalScrollBar().setUnitIncrement(16);
        result.getViewport().setBackground(view.getBackground());
        return result;
    }
    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    /** The activity follows the last message inside the same scrolling surface. */
    private final class ConversationStack extends JPanel implements Scrollable {
        ConversationStack() {
            super(new BorderLayout());
            setBackground(background);
            add(conversationArea, BorderLayout.CENTER);
            add(activityRow, BorderLayout.SOUTH);
        }
        @Override public Dimension getPreferredSize() {
            int width = getParent() instanceof JViewport viewport
                    ? viewport.getExtentSize().width : getWidth();
            if (width <= 0) width = 390;
            Insets insets = conversationArea.getInsets();
            View text = conversationArea.getUI().getRootView(conversationArea);
            text.setSize(Math.max(1, width - insets.left - insets.right), Float.MAX_VALUE);
            int height = (int) Math.ceil(text.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
            if (activityRow.isVisible()) height += activityRow.getPreferredSize().height;
            return new Dimension(width, height);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(390, 450); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 24);
        }
    }
    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }
    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        RoundedPanel(Color fill, LayoutManager layout, javax.swing.border.Border border) {
            super(layout); this.fill = fill; setOpaque(false); setBorder(border);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); g.dispose();
            super.paintComponent(graphics);
        }
    }
    private static final class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(10, 12, 10, 12); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(10, 12, 10, 12); return insets;
        }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.drawRoundRect(x, y, w - 1, h - 1, radius, radius); g.dispose();
        }
    }
    private static final class LineIcon implements Icon {
        private final String kind;
        LineIcon(String kind) { this.kind = kind; }
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
            switch (kind) {
                case "send" -> { g.drawLine(8, 13, 8, 3); g.drawLine(3, 8, 8, 3); g.drawLine(8, 3, 13, 8); }
                case "add" -> { g.drawLine(8, 3, 8, 13); g.drawLine(3, 8, 13, 8); }
                case "remove" -> { g.drawLine(4, 4, 12, 12); g.drawLine(12, 4, 4, 12); }
                default -> { g.drawRoundRect(2, 2, 12, 12, 2, 2); g.drawLine(8, 5, 8, 11); g.drawLine(5, 8, 11, 8); }
            }
            g.dispose();
        }
    }

    /** Styled, selectable transcript that preserves streamed text verbatim. */
    static final class Transcript extends JTextPane {
        private static final String THINKING = "Thinking…";
        private int thinkingStart = -1;
        private boolean agent;
        Transcript() {
            setEditorKit(new StyledEditorKit() {
                private final ViewFactory wrappingViews = element -> switch (element.getName()) {
                    case AbstractDocument.ContentElementName -> new LabelView(element) {
                        @Override public float getMinimumSpan(int axis) {
                            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                        }
                    };
                    case AbstractDocument.ParagraphElementName -> new ParagraphView(element);
                    case AbstractDocument.SectionElementName -> new BoxView(element, View.Y_AXIS);
                    case StyleConstants.ComponentElementName -> new ComponentView(element);
                    case StyleConstants.IconElementName -> new IconView(element);
                    default -> new LabelView(element);
                };
                @Override public ViewFactory getViewFactory() { return wrappingViews; }
            });
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public void setText(String text) {
            thinkingStart = -1;
            agent = false;
            super.setText(text);
        }
        void showThinking() {
            clearThinking();
            thinkingStart = getDocument().getLength();
            insert(THINKING, false, true);
            setCaretPosition(getDocument().getLength());
        }
        void clearThinking() {
            if (thinkingStart < 0) {
                return;
            }
            int start = thinkingStart;
            thinkingStart = -1;
            try { getDocument().remove(start, THINKING.length()); }
            catch (BadLocationException e) { throw new IllegalStateException(e); }
        }
        void append(String text) {
            if (text.isEmpty()) {
                return;
            }
            clearThinking();
            if (text.startsWith("You: ") && text.endsWith("\n\nCodex: ")) {
                agent = false;
                insert("YOU\n", true, true);
                insert(text.substring(5, text.length() - 9) + "\n\n", false, false);
                insert("CODEX\n", true, true);
                agent = true;
            } else {
                insert(text, false, !agent);
                if ("\n\n".equals(text)) agent = false;
            }
        }
        void appendAgentText(String text, boolean newMessage) {
            clearThinking();
            if (newMessage) insert("\n\n", false, false);
            agent = true;
            insert(text, false, false);
        }
        private void insert(String text, boolean bold, boolean muted) {
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setFontFamily(style, getFont().getFamily());
            StyleConstants.setFontSize(style, bold ? 10 : 13);
            StyleConstants.setBold(style, bold);
            Color fg = getForeground();
            Color bg = getBackground();
            StyleConstants.setForeground(style, muted ? new Color((fg.getRed() * 2 + bg.getRed()) / 3,
                    (fg.getGreen() * 2 + bg.getGreen()) / 3, (fg.getBlue() * 2 + bg.getBlue()) / 3) : fg);
            try { getStyledDocument().insertString(getDocument().getLength(), text, style); }
            catch (BadLocationException e) { throw new IllegalStateException(e); }
        }
    }
}
