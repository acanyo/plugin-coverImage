package cc.lik.coverImage.image;

import cc.lik.coverImage.service.SettingConfigGetter.ImageProcessingConfig;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.luciad.imageio.webp.WebPImageWriterSpi;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

/** CPU-bound image work; callers schedule this outside the reactive event loop. */
@Component
@RequiredArgsConstructor
public class ImageOptimizer {
    private final ImageDecoder decoder;

    public ImageFile optimize(ImageFile input, ImageProcessingConfig config) {
        if (!config.isEnabled()) {
            return input;
        }
        validate(config);
        MediaType detected = ImageFormat.detect(input.bytes());
        if (detected == null || !Set.of("jpeg", "png", "webp", "bmp").contains(detected.getSubtype())
            || ImageFormat.animated(input.bytes(), detected)) {
            return input;
        }
        String target = "webp".equals(config.getOutputFormat()) ? "webp" : detected.getSubtype();
        if (target.equals("bmp")) {
            return input;
        }
        try {
            BufferedImage decoded = decoder.decode(input.bytes(), detected);
            BufferedImage oriented = orient(decoded, orientation(input.bytes(), detected));
            BufferedImage scaled = scale(oriented, config.getMaxWidth(), target.equals("webp"));
            byte[] result = write(scaled, target, config.getQuality());
            // Converting format or dimensions is intentional; same-format compression must save bytes.
            if (target.equals(detected.getSubtype()) && scaled.getWidth() == oriented.getWidth()
                && result.length >= input.bytes().length) {
                return input;
            }
            MediaType type = MediaType.parseMediaType("image/" + target);
            String base = input.filename().replaceFirst("\\.[^.]+$", "");
            return new ImageFile(result, base + ImageFormat.extension(type), type);
        } catch (LinkageError error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "当前环境无法加载 WebP 编码器，请在图片处理设置中选择保留原格式或关闭处理", error);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片压缩失败：" + error.getMessage(), error);
        }
    }

    private void validate(ImageProcessingConfig config) {
        if (!Set.of("webp", "original").contains(String.valueOf(config.getOutputFormat()))
            || config.getQuality() < 1 || config.getQuality() > 100
            || config.getMaxWidth() < 0 || config.getMaxWidth() > 8192) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "图片处理设置无效：质量应为 1–100，最大宽度应为 0–8192");
        }
    }

    private byte[] write(BufferedImage image, String format, int quality) throws IOException {
        ImageWriter writer = format.equals("webp")
            ? new WebPImageWriterSpi().createWriterInstance()
            : ImageIO.getImageWritersByFormatName(format).next();
        try (var bytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = params.getCompressionTypes();
                if (types != null && types.length > 0) {
                    String type = types[0];
                    for (String candidate : types) {
                        if (candidate.toLowerCase(Locale.ROOT).contains("lossy")) {
                            type = candidate;
                        }
                    }
                    params.setCompressionType(type);
                }
                params.setCompressionQuality(format.equals("png") ? 0 : quality / 100f);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private int orientation(byte[] bytes, MediaType type) {
        if (!MediaType.IMAGE_JPEG.isCompatibleWith(type)) {
            return 1;
        }
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
                ? exif.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean swap = orientation >= 5;
        var output = new BufferedImage(swap ? height : width, swap ? width : height,
            image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx = switch (orientation) {
                    case 2, 3 -> width - 1 - x;
                    case 5, 8 -> y;
                    case 6, 7 -> height - 1 - y;
                    default -> x;
                };
                int dy = switch (orientation) {
                    case 3, 4 -> height - 1 - y;
                    case 5, 6 -> x;
                    case 7, 8 -> width - 1 - x;
                    default -> y;
                };
                output.setRGB(dx, dy, image.getRGB(x, y));
            }
        }
        return output;
    }

    private BufferedImage scale(BufferedImage image, int maxWidth, boolean webp) {
        double ratio = maxWidth == 0 ? 1 : Math.min(1, (double) maxWidth / image.getWidth());
        if (webp) {
            ratio = Math.min(ratio, 16383.0 / Math.max(image.getWidth(), image.getHeight()));
        }
        int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        // Normalize indexed/grayscale images to a color model supported by the WebP encoder.
        var output = new BufferedImage(width, height, image.getColorModel().hasAlpha()
            ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }
}
