package cc.lik.coverImage.model;

import org.springframework.http.MediaType;

/**
 * 图片类型
 */
public sealed interface ImageType {
    record UrlImage(String url) implements ImageType {}
    record Base64Image(String content, MediaType mediaType) implements ImageType {}
    record SvgImage(String content) implements ImageType {}
    record LocalImage(String path) implements ImageType {}
    record UnknownImage() implements ImageType {}
} 