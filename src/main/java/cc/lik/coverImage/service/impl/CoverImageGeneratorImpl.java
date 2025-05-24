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
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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

    private static final String DEFAULT_GRADIENT = "<stop offset=\"0%\" stop-color=\"#aee2ff\"/><stop offset=\"100%\" stop-color=\"#7fbbf7\"/>";
    private static final int TARGET_WIDTH = 200;
    private static final int TARGET_HEIGHT = 200;
    private static final int SVG_WIDTH = 1200;
    private static final int SVG_HEIGHT = 630;

    @Override
    public Mono<String> generateCoverImage(Post post) {
        Map<String, String> annotations = post.getMetadata().getAnnotations();
        String title1 = annotations.getOrDefault("coverImgTitle1", "");
        String title2 = annotations.getOrDefault("coverImgTitle2", "");
        String coverImgLogoAnnotation = annotations.getOrDefault("coverImgLogo", "");
        String color = annotations.getOrDefault("coverImgColor", "");

        return getLogoBase64(coverImgLogoAnnotation)
            .flatMap(logoBase64 -> generateSvg(title1, title2, logoBase64, color))
            .flatMap(svg -> uploadSvg(svg, post));
    }

    private Mono<String> generateSvg(String title1, String title2, String logoBase64, String color) {
        return Mono.fromCallable(() -> {
            Resource resource = resourceLoader.getResource("classpath:/static/cover.svg");
            try (InputStream is = resource.getInputStream()) {
                String svg = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                Map<String, String> grad = parseGradient(color);
                return svg.replace("${title1}", title1)
                         .replace("${title2}", title2)
                         .replace("${logoBase64}", logoBase64)
                         .replace("${gradientTransform}", grad.get("gradientTransform"))
                         .replace("${stops}", grad.get("stops"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> uploadSvg(String svg, Post post) {
        ByteArrayInputStream svgInputStream = new ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        InputStreamResource svgResource = new InputStreamResource(svgInputStream);
        Flux<DataBuffer> dataBufferFlux = DataBufferUtils.read(svgResource, dataBufferFactory, 8192);
        String uniqueFilename = "cover-" + System.currentTimeMillis() + ".svg";
        return imageTransferService.updateFile(dataBufferFlux, post, uniqueFilename, MediaType.parseMediaType("image/svg+xml"));
    }

    private Mono<String> getLogoBase64(String logoIdentifier) {
        if (logoIdentifier == null || logoIdentifier.isEmpty()) {
            return Mono.just("");
        }

        String fullLogoUrl = logoIdentifier.startsWith("http") ? 
            logoIdentifier : externalUrlSupplier.getRaw() + logoIdentifier;

        try {
            URL url = new URL(fullLogoUrl);
            return Mono.fromCallable(() -> {
                BufferedImage originalImage = ImageIO.read(url);
                if (originalImage == null) {
                    return "";
                }

                BufferedImage resizedImage = resizeImage(originalImage);
                String formatName = detectImageFormat(url);
                return encodeToBase64(resizedImage, formatName);
            }).subscribeOn(Schedulers.boundedElastic())
              .onErrorResume(e -> {
                  log.error("Failed to process logo image: {}", e.getMessage());
                  return Mono.just("");
              });
        } catch (Exception e) {
            log.error("Invalid logo URL: {}", e.getMessage());
            return Mono.just("");
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
        g.dispose();
        return resizedImage;
    }

    private String detectImageFormat(URL url) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(url.openStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            return readers.hasNext() ? readers.next().getFormatName() : "png";
        }
    }

    private String encodeToBase64(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, outputStream);
        String base64String = Base64.getEncoder().encodeToString(outputStream.toByteArray());
        MediaType mediaType = getMediaTypeByFormatName(formatName);
        return String.format("data:%s;base64,%s", mediaType.toString(), base64String);
    }

    private MediaType getMediaTypeByFormatName(String formatName) {
        return switch (formatName.toLowerCase()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private Map<String, String> parseGradient(String css) {
        Map<String, String> result = new java.util.HashMap<>();
        if (css == null || css.isEmpty()) {
            result.put("gradientTransform", "");
            result.put("stops", DEFAULT_GRADIENT);
            return result;
        }

        try {
            Pattern p = Pattern.compile("(\\d+)deg,\\s*(.*)");
            Matcher m = p.matcher(css);
            if (m.find()) {
                int angle = Integer.parseInt(m.group(1));
                String stopsStr = m.group(2);
                String gradientTransform = String.format("gradientTransform=\"rotate(%d %d %d)\"", 
                    angle, SVG_WIDTH / 2, SVG_HEIGHT / 2);
                result.put("gradientTransform", gradientTransform);
                result.put("stops", String.join("", parseStops(stopsStr)));
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to parse gradient: {}", css);
        }

        result.put("gradientTransform", "");
        result.put("stops", DEFAULT_GRADIENT);
        return result;
    }

    private ArrayList<String> parseStops(String stopsStr) {
        String[] stopsArr = stopsStr.split(",");
        ArrayList<String> stopsList = new ArrayList<>();
        for (String stop : stopsArr) {
            String[] parts = stop.trim().split(" ");
            if (parts.length == 2) {
                stopsList.add(String.format("<stop offset=\"%s\" stop-color=\"%s\"/>", parts[1], parts[0]));
            } else if (parts.length == 1) {
                stopsList.add(String.format("<stop offset=\"%d%%\" stop-color=\"%s\"/>", 
                    (int)(100.0 * stopsList.size()/(stopsArr.length-1)), parts[0]));
            }
        }
        return stopsList;
    }
} 