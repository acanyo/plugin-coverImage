package cc.lik.coverImage.util;

import java.util.Optional;
import cc.lik.coverImage.constant.ImageConstants;
import org.springframework.http.MediaType;

/**
 * 图片工具类
 */
public final class ImageUtils {
    private ImageUtils() {
        throw new AssertionError("工具类不应被实例化");
    }

    /**
     * 从URL获取文件扩展名
     */
    public static String getFileExtension(String url) {
        return Optional.ofNullable(url)
            .map(u -> {
                int qIdx = u.indexOf('?');
                var path = (qIdx >= 0) ? u.substring(0, qIdx) : u;
                int dotIdx = path.lastIndexOf('.');
                return (dotIdx >= 0) ? path.substring(dotIdx) : null;
            })
            .orElse(ImageConstants.DEFAULT_EXTENSION);
    }

    /**
     * 从MediaType获取文件扩展名
     */
    public static String getFileExtensionFromMediaType(String mediaType) {
        return ImageConstants.MEDIA_TYPE_TO_EXTENSION.getOrDefault(mediaType, ImageConstants.DEFAULT_EXTENSION);
    }

    /**
     * 从URL获取MediaType
     */
    public static MediaType getMediaTypeFromUrl(String url) {
        var extension = getFileExtension(url).toLowerCase();
        return ImageConstants.EXTENSION_TO_MEDIA_TYPE.getOrDefault(extension, MediaType.IMAGE_JPEG);
    }
} 