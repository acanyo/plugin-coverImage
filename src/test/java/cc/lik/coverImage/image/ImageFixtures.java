package cc.lik.coverImage.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import javax.imageio.ImageIO;

public final class ImageFixtures {
    private ImageFixtures() {
    }

    public static byte[] png(int width, int height, boolean alpha) throws Exception {
        var image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var random = new Random(18);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (alpha && x < width / 2 ? 0 : 0xff000000) | random.nextInt(0x1000000));
            }
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    public static byte[] orientedJpeg(int orientation) throws Exception {
        var image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        byte[] exif = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(new byte[]{'E', 'x', 'i', 'f', 0, 0, 'I', 'I'})
            .putShort((short) 42).putInt(8).putShort((short) 1)
            .putShort((short) 0x112).putShort((short) 3).putInt(1)
            .putShort((short) orientation).putShort((short) 0).putInt(0).array();
        byte[] raw = jpeg.toByteArray();
        var output = new ByteArrayOutputStream();
        output.write(raw, 0, 2);
        output.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, 34});
        output.write(exif);
        output.write(raw, 2, raw.length - 2);
        return output.toByteArray();
    }
}
