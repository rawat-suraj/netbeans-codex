package com.suraj.netbeans.codex;

import java.awt.*;
import javax.swing.Icon;

/** A scalable terminal mark for the Codex panel, painted in the current theme. */
final class CodexLogo implements Icon {
    private final int size;

    CodexLogo(int size) { this.size = size; }
    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.scale(size / 32.0, size / 32.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color background = component.getBackground();
        boolean dark = background != null
                && background.getRed() + background.getGreen() + background.getBlue() < 384;
        g.setColor(dark ? new Color(0xe6e6e6) : new Color(0x242424));
        g.fillRoundRect(0, 0, 32, 32, 9, 9);
        g.setColor(dark ? new Color(0x242424) : Color.WHITE);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(8, 10, 14, 16);
        g.drawLine(14, 16, 8, 22);
        g.drawLine(18, 22, 25, 22);
        g.dispose();
    }
}
