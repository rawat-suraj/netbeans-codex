package com.suraj.netbeans.codex;

import com.google.gson.JsonObject;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** An immutable image snapshot, sent as vision input rather than source-code text. */
record ImageAttachment(String url, int bytes, int width, int height, ImageIcon thumbnail) {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static void checkDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || (long) width * height > 25_000_000) {
            throw new IOException("Image dimensions are invalid or exceed 25 megapixels.");
        }
    }
    static ImageAttachment fromImage(Image source) throws IOException {
        ImageIcon loaded = new ImageIcon(source);
        int width = loaded.getIconWidth(), height = loaded.getIconHeight();
        checkDimensions(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.drawImage(source, 0, 0, null); } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("Cannot encode this clipboard image.");
        if (output.size() > MAX_BYTES) throw new IOException("Image exceeds 8 MB. Use a smaller screenshot.");
        double scale = Math.min(1, 64.0 / Math.max(width, height));
        BufferedImage thumbnail = new BufferedImage(Math.max(1, (int) (width * scale)),
                Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
        var thumbnailGraphics = thumbnail.createGraphics();
        try { thumbnailGraphics.drawImage(image, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null); }
        finally { thumbnailGraphics.dispose(); }
        ImageIcon preview = new ImageIcon(thumbnail);
        return new ImageAttachment("data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()),
                output.size(), width, height, preview);
    }
    static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|gif|bmp)$");
    }
    static ImageAttachment fromFile(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Image file exceeds 8 MB.");
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Cannot open this image.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                checkDimensions(reader.getWidth(0), reader.getHeight(0));
                return fromImage(reader.read(0));
            } finally { reader.dispose(); }
        }
    }
    JsonObject input() {
        JsonObject input = new JsonObject();
        input.addProperty("type", "image");
        input.addProperty("url", url);
        return input;
    }
}
