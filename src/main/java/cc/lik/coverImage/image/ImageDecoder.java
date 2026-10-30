package cc.lik.coverImage.image;

import com.luciad.imageio.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Decode only after checking dimensions; avoid installing plugin codecs in a global registry. */
@Component
public class ImageDecoder {
    public BufferedImage decode(byte[] bytes, MediaType type) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader;
            if (ImageFormat.WEBP.isCompatibleWith(type)) {
                reader = new WebPImageReaderSpi().createReaderInstance();
            } else {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    throw new IOException("无法解码图片");
                }
                reader = readers.next();
            }
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) {
                    throw new IOException("图片不能超过 4000 万像素");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
