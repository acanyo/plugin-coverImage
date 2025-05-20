package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.CoverImageGenerator;
import cc.lik.coverImage.service.ImageTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.ResourceLoader;
import reactor.core.scheduler.Schedulers;
import org.springframework.http.MediaType;
import run.halo.app.infra.ExternalUrlSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Base64;
import java.net.URL;
import javax.imageio.ImageIO;
import java.awt.FontMetrics;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverImageGeneratorImpl implements CoverImageGenerator {
    private final ImageTransferService imageTransferService;
    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private final ResourceLoader resourceLoader;
    private final ExternalUrlSupplier externalUrlSupplier;
    @Override
    public Mono<String> generateCoverImage(Post post) {
        Map<String, String> annotations = post.getMetadata().getAnnotations();
        String title1 = annotations.getOrDefault("coverImgTitle1", "");
        String title2 = annotations.getOrDefault("coverImgTitle2", "");
        String coverImgLogoAnnotation = annotations.getOrDefault("coverImgLogo", "");
        String color = annotations.getOrDefault("coverImgColor", "");

        Mono<String> logoBase64Mono = getLogoBase64(coverImgLogoAnnotation);

        return logoBase64Mono.flatMap(logoBase64 -> {
            return Mono.fromCallable(() -> {
                Resource resource = resourceLoader.getResource("classpath:/static/cover.svg");
                try (InputStream is = resource.getInputStream()) {
                    String svg = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    Map<String, String> grad = parseGradient(color, 1200, 630);
                    // 直接替换占位符
                    svg = svg.replace("${title1}", title1)
                             .replace("${title2}", title2)
                             .replace("${logoBase64}", logoBase64)
                              .replace("${gradientTransform}", grad.getOrDefault("gradientTransform", ""))
                              .replace("${stops}", grad.getOrDefault("stops", ""));
                    return svg;
                }
            }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(svg -> {
                ByteArrayInputStream svgInputStream = new ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                InputStreamResource svgResource = new InputStreamResource(svgInputStream);
                Flux<DataBuffer> dataBufferFlux = DataBufferUtils.read(svgResource, dataBufferFactory, 8192);
                String uniqueFilename = "cover-" + System.currentTimeMillis() + ".svg";
                return imageTransferService.updateFile(dataBufferFlux, post, uniqueFilename, MediaType.parseMediaType("image/svg+xml"));
            });
        });
    }

    // 获取Logo图片数据并进行Base64编码
    private Mono<String> getLogoBase64(String logoIdentifier) {
        if (logoIdentifier == null || logoIdentifier.isEmpty()) {
            return Mono.just(""); // 没有Logo则返回空字符串
        }
        // 假设logoIdentifier是完整的URL或相对路径，需要拼接externalUrlSupplier
        String fullLogoUrl;
        if (logoIdentifier.startsWith("http://") || logoIdentifier.startsWith("https://")) {
            fullLogoUrl = logoIdentifier;
        } else {
            // 假设logoIdentifier是/upload/... 或 static/... 这样的路径
            fullLogoUrl = externalUrlSupplier.getRaw() + logoIdentifier;
        }

        try {
            URL url = new URL(fullLogoUrl);
            return Mono.fromCallable(() -> {
                log.info("Fetching logo image from: {}", fullLogoUrl);
                BufferedImage originalImage = ImageIO.read(url);
                if (originalImage == null) {
                    log.warn("ImageIO.read returned null for: {}", fullLogoUrl);
                    return "";
                }
                log.info("Successfully read original logo image.");

                // Resize the image to 200x200
                int targetWidth = 200;
                int targetHeight = 200;
                BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = resizedImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
                g.dispose();
                log.info("Successfully resized logo image.");

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                // 尝试猜测图片格式，默认为png
                String formatName = "png";
                try (ImageInputStream iis = ImageIO.createImageInputStream(url.openStream())) {
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                    if (readers.hasNext()) {
                        ImageReader reader = readers.next();
                        formatName = reader.getFormatName();
                        log.info("Detected image format: {}", formatName);
                        iis.close(); // Close stream after reading format
                    }
                } catch (IOException e) {
                    log.warn("Could not determine image format for {}: {}", fullLogoUrl, e.getMessage());
                    // Fallback to png
                }

                try {
                    ImageIO.write(resizedImage, formatName, outputStream); // Use resizedImage
                    String base64String = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    log.info("Successfully encoded logo image to Base64. Generated Base64 string length: {}", base64String.length());
                    // 根据实际格式调整data URL前缀
                    MediaType mediaType = getMediaTypeByFormatName(formatName);
                    String dataUri = String.format("data:%s;base64,%s", mediaType.toString(), base64String);
                    log.info("Generated Data URI (first 50 chars): {}", dataUri.substring(0, Math.min(dataUri.length(), 50)));
                    return dataUri;
                } catch (IOException e) {
                     log.error("Failed to write or encode image: {}", e.getMessage());
                     return "";
                }

            }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(e -> {
                log.warn("Failed to get and encode logo image {}: {}", fullLogoUrl, e.getMessage());
                return Mono.just(""); // 获取或编码失败则返回空字符串
            });
        } catch (Exception e) {
            log.warn("Invalid logo URL or other error {}: {}", fullLogoUrl, e.getMessage());
            return Mono.just(""); // URL无效或其他错误则返回空字符串
        }
    }

    private MediaType getMediaTypeByFormatName(String formatName) {
        switch (formatName.toLowerCase()) {
            case "png":
                return MediaType.IMAGE_PNG;
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "bmp":
                return MediaType.parseMediaType("image/bmp");
            case "webp":
                 return MediaType.parseMediaType("image/webp");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // 解析CSS渐变字符串，返回gradientTransform和stops
    private Map<String, String> parseGradient(String css, int width, int height) {
        Map<String, String> result = new java.util.HashMap<>();
        if (css == null || css.isEmpty()) {
            result.put("gradientTransform", "");
            result.put("stops", "<stop offset=\"0%\" stop-color=\"#aee2ff\"/><stop offset=\"100%\" stop-color=\"#7fbbf7\"/>");
            return result;
        }
        try {
            Pattern p = Pattern.compile("(\\d+)deg,\\s*(.*)");
            Matcher m = p.matcher(css);
            if (m.find()) {
                int angle = Integer.parseInt(m.group(1));
                String stopsStr = m.group(2);
                // SVG rotate(angle cx cy)
                int cx = width / 2;
                int cy = height / 2;
                String gradientTransform = String.format("gradientTransform=\"rotate(%d %d %d)\"", angle, cx, cy);
                result.put("gradientTransform", gradientTransform);
                // 解析 stops
                ArrayList<String> stopsList = stopsArr(stopsStr);
                result.put("stops", String.join("", stopsList));
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to parse gradient: {}", css);
        }
        // fallback
        result.put("gradientTransform", "");
        result.put("stops", "<stop offset=\"0%\" stop-color=\"#aee2ff\"/><stop offset=\"100%\" stop-color=\"#7fbbf7\"/>");
        return result;
    }

    private ArrayList<String> stopsArr(String stopsStr) {
        String[] stopsArr = stopsStr.split(",");
        ArrayList<String> stopsList = new ArrayList<>();
        for (String stop : stopsArr) {
            String[] parts = stop.trim().split(" ");
            if (parts.length == 2) {
                stopsList.add(String.format("<stop offset=\"%s\" stop-color=\"%s\"/>", parts[1], parts[0]));
            } else if (parts.length == 1) {
                stopsList.add(String.format("<stop offset=\"%d%%\" stop-color=\"%s\"/>", (int)(100.0 * stopsList.size()/(stopsArr.length-1)), parts[0]));
            }
        }
        return stopsList;
    }
} 