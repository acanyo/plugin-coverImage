package cc.lik.coverImage.model;

import org.springframework.http.MediaType;

public record ImageData(
    String content,
    MediaType mediaType,
    String filename
) {
    public static ImageData of(String content, MediaType mediaType, String filename) {
        return new ImageData(content, mediaType, filename);
    }
} 