package com.suraj.netbeans.codex;

import java.awt.Color;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.JButton;

public final class ImagePasteChecks {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static JButton imageRemoveButton(Container container, String name) {
        for (var child : container.getComponents()) {
            if (child instanceof JButton button && ("Remove " + name).equals(button.getToolTipText())) return button;
            if (child instanceof Container nested) {
                JButton button = imageRemoveButton(nested, name);
                if (button != null) return button;
            }
        }
        return null;
    }
    private static Transferable value(DataFlavor flavor, Object value) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{flavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor requested) { return flavor.equals(requested); }
            @Override public Object getTransferData(DataFlavor requested) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(requested)) throw new UnsupportedFlavorException(requested);
                return value;
            }
        };
    }
    public static void main(String[] args) throws Exception {
        BufferedImage screenshot = new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB);
        screenshot.setRGB(3, 4, Color.RED.getRGB());
        ImageAttachment encoded = ImageAttachment.fromImage(screenshot);
        check(encoded.input().get("type").getAsString().equals("image"), "Use native image input, not a filename in the prompt");
        byte[] png = Base64.getDecoder().decode(encoded.url().substring("data:image/png;base64,".length()));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        check(decoded.getWidth() == 30 && decoded.getHeight() == 20 && decoded.getRGB(3, 4) == Color.RED.getRGB(), "Preserve clipboard pixels in the vision payload");
        Path file = Path.of("target/image-paste-check.png").toAbsolutePath();
        ImageIO.write(screenshot, "png", file.toFile());
        SwingUtilities.invokeAndWait(() -> {
            CodexChatPanel panel = new CodexChatPanel();
            panel.setConnectionState(CodexChatPanel.ConnectionState.READY);
            TransferHandler handler = panel.promptArea.getTransferHandler();
            check(handler.importData(new TransferHandler.TransferSupport(panel.promptArea, value(DataFlavor.imageFlavor, screenshot))), "Paste a screenshot through the standard transfer action");
            check(panel.hasImages() && panel.attachmentModel.size() == 1, "Show the pasted image as an attachment");
            check(panel.sendBtn.isEnabled(), "Allow sending an image without typed text");
            check(panel.promptArea.getText().isEmpty(), "Do not paste binary/base64 text into the composer");
            check(handler.importData(panel.promptArea, new StringSelection("Normal text")), "Preserve ordinary text paste");
            check(panel.promptArea.getText().equals("Normal text"), "Paste the original text unchanged");
            panel.promptArea.selectAll();
            Clipboard clipboard = new Clipboard("isolated image-paste check");
            handler.exportToClipboard(panel.promptArea, clipboard, TransferHandler.COPY);
            try { check(clipboard.getData(DataFlavor.stringFlavor).equals("Normal text"), "Preserve text copy"); }
            catch (Exception error) { throw new RuntimeException(error); }
            check(handler.importData(panel.promptArea, value(DataFlavor.javaFileListFlavor, java.util.List.of(file.toFile()))), "Paste a copied image file");
            check(panel.attachmentModel.size() == 2, "Keep separate pasted image attachments");
            panel.setConnectionState(CodexChatPanel.ConnectionState.BUSY);
            check(!handler.importData(panel.promptArea, value(DataFlavor.imageFlavor, screenshot)), "Prevent changing image inputs during a running turn");
            check(panel.attachmentModel.size() == 2, "Busy paste must leave attachments unchanged");
            panel.setConnectionState(CodexChatPanel.ConnectionState.READY);
            panel.addImage(encoded);
            panel.addImage(encoded);
            check(!handler.importData(panel.promptArea, value(DataFlavor.imageFlavor, screenshot)), "Enforce the four-image limit");
            String firstImage = panel.attachmentModel.get(0);
            String otherImage = panel.attachmentModel.get(1);
            panel.attachmentModel.addElement("C:\\example\\Source.java");
            check(panel.attachmentsList.getModel().getSize() == 1, "Only source files use the selectable list");
            JButton remove = imageRemoveButton(panel, firstImage);
            check(remove != null && remove.getX() > 0 && remove.getY() == 0, "Put a real remove control on the thumbnail's top-right");
            check(panel.attachmentsList.isSelectionEmpty(), "Image removal must not require selecting a list row");
            remove.doClick();
            check(panel.imageAttachment(firstImage) == null && !panel.attachmentModel.contains(firstImage), "Clicking the cross removes that image and its payload");
            check(panel.attachmentModel.contains(otherImage) && panel.attachmentModel.contains("C:\\example\\Source.java"), "Leave other images and source files intact");
            panel.attachmentModel.clear();
            panel.promptArea.setText("");
            check(!panel.hasImages() && !panel.sendBtn.isEnabled(), "Removing attachments must clear image data and reset Send");
        });
        Files.delete(file);
        System.out.println("PASS: screenshot/image-file paste, thumbnails, native image payload and pixel preservation, normal text copy/paste, image-only send, removal and limits");
    }
}
