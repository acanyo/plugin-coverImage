package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

public interface CodeSphereService {
    Mono<String> generateImage(Post post, String model, String size, String style, boolean watermark);
}
