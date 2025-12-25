package cc.lik.coverImage.constant;

import java.util.Map;
import org.springframework.http.MediaType;

public class ImageConstants {
    public static final String COVER_PREFIX = "cover-";
    public static final String DEFAULT_EXTENSION = ".jpg";
    
    public static final Map<String, String> MEDIA_TYPE_TO_EXTENSION = Map.of(
        "image/png", ".png",
        "image/gif", ".gif",
        "image/svg+xml", ".svg",
        "image/jpeg", ".jpg"
    );
    
    public static final Map<String, MediaType> EXTENSION_TO_MEDIA_TYPE = Map.of(
        ".png", MediaType.IMAGE_PNG,
        ".gif", MediaType.IMAGE_GIF,
        ".svg", MediaType.parseMediaType("image/svg+xml"),
        ".jpg", MediaType.IMAGE_JPEG
    );
    
    public static final String ERROR_NO_CONFIG = "未配置随机图片类型";
    public static final String ERROR_NO_IMAGE = "未找到文章中的图片";
    public static final String ERROR_INVALID_BASE64 = "Invalid base64 format";
    public static final String ERROR_UNSUPPORTED_FORMAT = "Unsupported image content format";
} 