package cc.lik.coverImage.image;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Detect formats using file signatures, including URLs with no file extension. */
public final class ImageFormat {
    public static final MediaType WEBP = MediaType.parseMediaType("image/webp");
    private ImageFormat() {
    }

    public static MediaType detect(byte[] bytes) {
        if (bytes.length >= 3 && unsigned(bytes[0]) == 255 && unsigned(bytes[1]) == 216
            && unsigned(bytes[2]) == 255) {
            return MediaType.IMAGE_JPEG;
        }
        if (bytes.length >= 8 && unsigned(bytes[0]) == 137
            && text(bytes, 1, 7).equals("PNG\r\n\u001a\n")) {
            return MediaType.IMAGE_PNG;
        }
        if (bytes.length >= 6 && (text(bytes, 0, 6).equals("GIF87a")
            || text(bytes, 0, 6).equals("GIF89a"))) {
            return MediaType.IMAGE_GIF;
        }
        if (bytes.length >= 12 && text(bytes, 0, 4).equals("RIFF")
            && text(bytes, 8, 4).equals("WEBP")) {
            return WEBP;
        }
        if (bytes.length >= 2 && text(bytes, 0, 2).equals("BM")) {
            return MediaType.parseMediaType("image/bmp");
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 1024), StandardCharsets.UTF_8)
            .stripLeading();
        if (prefix.startsWith("<svg") || (prefix.startsWith("<?xml") && prefix.contains("<svg"))) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return null;
    }

    public static String extension(MediaType type) {
        return switch (type.getSubtype()) {
            case "jpeg" -> ".jpg";
            case "svg+xml" -> ".svg";
            default -> "." + type.getSubtype();
        };
    }

    public static boolean animated(byte[] bytes, MediaType type) {
        if (MediaType.IMAGE_GIF.isCompatibleWith(type)) {
            return true;
        }
        if (WEBP.isCompatibleWith(type)) {
            return bytes.length >= 21 && text(bytes, 12, 4).equals("VP8X")
                && (bytes[20] & 2) != 0;
        }
        if (MediaType.IMAGE_PNG.isCompatibleWith(type)) {
            int offset = 8;
            while (offset <= bytes.length - 12) {
                long size = ((long) unsigned(bytes[offset]) << 24)
                    | ((long) unsigned(bytes[offset + 1]) << 16)
                    | ((long) unsigned(bytes[offset + 2]) << 8) | unsigned(bytes[offset + 3]);
                String chunk = text(bytes, offset + 4, 4);
                if (chunk.equals("acTL")) {
                    return true;
                }
                if (chunk.equals("IDAT") || size > bytes.length - offset - 12) {
                    break;
                }
                offset += (int) size + 12;
            }
        }
        return false;
    }

    private static int unsigned(byte value) {
        return value & 255;
    }

    private static String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }
}
