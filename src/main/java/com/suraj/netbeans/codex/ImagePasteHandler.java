package com.suraj.netbeans.codex;

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.io.File;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/** Adds image paste while preserving the text component's regular clipboard actions. */
final class ImagePasteHandler extends TransferHandler {
    private final TransferHandler text;
    private final BooleanSupplier allowed;
    private final Consumer<ImageAttachment> attach;
    private final Consumer<String> error;

    ImagePasteHandler(TransferHandler text, BooleanSupplier allowed,
            Consumer<ImageAttachment> attach, Consumer<String> error) {
        this.text = text;
        this.allowed = allowed;
        this.attach = attach;
        this.error = error;
    }
    @Override public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.imageFlavor) || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || (text != null && text.canImport(support));
    }
    @Override public boolean canImport(JComponent component, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) if (DataFlavor.imageFlavor.equals(flavor) || DataFlavor.javaFileListFlavor.equals(flavor)) return true;
        return text != null && text.canImport(component, flavors);
    }
    @Override public boolean importData(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) return importImage(support.getTransferable());
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            Boolean result = importImageFile(support.getTransferable());
            if (result != null) return result;
        }
        return text != null && text.importData(support);
    }
    @Override public boolean importData(JComponent component, Transferable value) {
        if (value.isDataFlavorSupported(DataFlavor.imageFlavor)) return importImage(value);
        if (value.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            Boolean result = importImageFile(value);
            if (result != null) return result;
        }
        return text != null && text.importData(component, value);
    }
    private boolean importImage(Transferable value) {
        if (!allowed.getAsBoolean()) { error.accept("Wait for the current reply before attaching an image."); return false; }
        try {
            attach.accept(ImageAttachment.fromImage((Image) value.getTransferData(DataFlavor.imageFlavor)));
            return true;
        } catch (Exception failure) {
            error.accept("Cannot paste image: " + failure.getMessage());
            return false;
        }
    }
    private Boolean importImageFile(Transferable value) {
        try {
            Object payload = value.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(payload instanceof java.util.List<?> files) || files.size() != 1
                    || !(files.get(0) instanceof File file) || !ImageAttachment.isImage(file.toPath())) return null;
            if (!allowed.getAsBoolean()) { error.accept("Wait for the current reply before attaching an image."); return false; }
            attach.accept(ImageAttachment.fromFile(file.toPath()));
            return true;
        } catch (Exception failure) { error.accept("Cannot paste image file: " + failure.getMessage()); return false; }
    }
    @Override public int getSourceActions(JComponent component) { return text == null ? NONE : text.getSourceActions(component); }
    @Override public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (text != null) text.exportToClipboard(component, clipboard, action);
    }
    @Override public void exportAsDrag(JComponent component, java.awt.event.InputEvent event, int action) {
        if (text != null) text.exportAsDrag(component, event, action);
    }
}
