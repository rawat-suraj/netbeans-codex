package com.suraj.netbeans.codex;

import java.awt.Component;
import java.awt.Container;
import java.awt.CardLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.JPanel;

/** Reproduces immediate validation performed by CardLayout.show in a visible IDE. */
public final class TranscriptLayoutChecks {
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> asynchronousError = new AtomicReference<>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            asynchronousError.compareAndSet(null, error);
            error.printStackTrace();
        });
        CodexChatPanel[] testPanel = new CodexChatPanel[1];
        JPanel[] testCards = new JPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                CodexChatPanel panel = new CodexChatPanel();
                testPanel[0] = panel;
                panel.setSize(390, 850);
                try {
                    var field = CodexChatPanel.class.getDeclaredField("cards");
                    field.setAccessible(true);
                    JPanel cards = (JPanel) field.get(panel);
                    testCards[0] = cards;
                    CardLayout immediateValidation = new CardLayout() {
                        @Override public void show(Container parent, String name) {
                            super.show(parent, name);
                            layout(panel);
                        }
                    };
                    cards.setLayout(immediateValidation);
                    immediateValidation.addLayoutComponent(cards.getComponent(0), "welcome");
                    immediateValidation.addLayoutComponent(cards.getComponent(1), "conversation");
                    String text = "Review this file: D:\\project\\src\\Controller.php\n"
                            + "Unicode: हिन्दी العربية 中文 😀 café — “quoted”\n"
                            + "LongIdentifier".repeat(30) + "\n";
                    for (int round = 0; round < 3; round++) {
                        panel.conversationArea.setText("");
                        for (int message = 0; message < 10; message++) {
                            panel.conversationArea.appendHistoryMessage(message % 2 == 0, text.repeat(2));
                        }
                        panel.setSize(round % 2 == 0 ? 280 : 600, 850);
                        panel.validate();
                        layout(panel);
                        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try { panel.paint(graphics); } finally { graphics.dispose(); }
                    }
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            // Drain queued card/caret updates, then exercise the now-visible transcript.
            SwingUtilities.invokeAndWait(() -> {
                if (!testCards[0].getComponent(1).isVisible()) throw new AssertionError("Restored transcript must be visible");
                layout(testPanel[0]);
                testPanel[0].conversationArea.setCaretPosition(testPanel[0].conversationArea.getDocument().getLength());
                BufferedImage image = new BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { testPanel[0].paint(graphics); } finally { graphics.dispose(); }
            });
            SwingUtilities.invokeAndWait(() -> { });
            if (asynchronousError.get() != null) throw new AssertionError("Deferred Swing layout failed", asynchronousError.get());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        System.out.println("PASS: immediate card validation, Unicode history, narrow/wide layout and painting");
    }
}
