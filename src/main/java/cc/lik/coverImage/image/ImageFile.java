package cc.lik.coverImage.image;

import org.springframework.http.MediaType;

/** The bytes, filename and content type must always describe the same image. */
public record ImageFile(byte[] bytes, String filename, MediaType mediaType) {
}
