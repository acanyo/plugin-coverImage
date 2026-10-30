package cc.lik.coverImage.image;

import static org.junit.jupiter.api.Assertions.*;

import cc.lik.coverImage.service.SettingConfigGetter.ImageProcessingConfig;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class ImageOptimizerTest {
    private final ImageOptimizer optimizer = new ImageOptimizer(new ImageDecoder());

    @Test
    void disabledProcessingPreservesOriginalBytesAndFilename() throws Exception {
        var file = new ImageFile(ImageFixtures.png(20, 10, false), "original.png", MediaType.IMAGE_PNG);
        assertSame(file, optimizer.optimize(file, new ImageProcessingConfig()));
    }

    @Test
    void webpEncodingResizesProportionallyAndPreservesTransparency() throws Exception {
        byte[] source = ImageFixtures.png(400, 200, true);
        var config = enabled();
        config.setMaxWidth(200);
        ImageFile output = optimizer.optimize(new ImageFile(source, "cover.png", MediaType.IMAGE_PNG), config);
        assertEquals("cover.webp", output.filename());
        assertEquals(ImageFormat.WEBP, output.mediaType());
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(output.bytes()));
        assertTrue(output.bytes().length < source.length);
        var reader = new WebPImageReaderSpi().createReaderInstance();
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(output.bytes()))) {
            reader.setInput(stream);
            var decoded = reader.read(0);
            assertEquals(200, decoded.getWidth());
            assertEquals(100, decoded.getHeight());
            assertEquals(0, decoded.getRGB(0, 0) >>> 24);
        } finally {
            reader.dispose();
        }
    }

    @Test
    void jpegExifOrientationSurvivesConversionToWebp() throws Exception {
        for (int orientation : new int[]{5, 6, 7, 8}) {
            ImageFile output = optimizer.optimize(new ImageFile(ImageFixtures.orientedJpeg(orientation),
                "portrait.jpg", MediaType.IMAGE_JPEG), enabled());
            var reader = new WebPImageReaderSpi().createReaderInstance();
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(output.bytes()))) {
                reader.setInput(stream);
                assertEquals(20, reader.getWidth(0));
                assertEquals(40, reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void originalFormatKeepsPngAndDoesNotEnlargeSmallImages() throws Exception {
        var config = enabled();
        config.setOutputFormat("original");
        config.setMaxWidth(1000);
        byte[] source = ImageFixtures.png(50, 25, false);
        var output = optimizer.optimize(new ImageFile(source, "cover.png", MediaType.IMAGE_PNG), config);
        var decoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        assertEquals("cover.png", output.filename());
        assertEquals(50, decoded.getWidth());
        assertTrue(output.bytes().length <= source.length);
    }

    @Test
    void animationsAndSvgAreNotFlattened() {
        byte[] animatedWebp = new byte[30];
        System.arraycopy("RIFF1234WEBPVP8X".getBytes(StandardCharsets.US_ASCII), 0, animatedWebp, 0, 16);
        animatedWebp[20] = 2;
        for (ImageFile file : new ImageFile[]{
            new ImageFile("GIF89a".getBytes(StandardCharsets.US_ASCII), "animated.gif", MediaType.IMAGE_GIF),
            new ImageFile(animatedWebp, "animated.webp", ImageFormat.WEBP),
            new ImageFile("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8),
                "vector.svg", MediaType.parseMediaType("image/svg+xml"))
        }) {
            assertSame(file, optimizer.optimize(file, enabled()));
        }
    }

    @Test
    void invalidQualityIsRejected() {
        var config = enabled();
        config.setQuality(101);
        assertThrows(ResponseStatusException.class, () -> optimizer.optimize(
            new ImageFile(new byte[0], "cover.png", MediaType.IMAGE_PNG), config));
    }

    private ImageProcessingConfig enabled() {
        var config = new ImageProcessingConfig();
        config.setEnabled(true);
        return config;
    }
}
