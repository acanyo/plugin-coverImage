package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

public interface AIImageGenerator {

    Mono<String> generateImage(Post post, String size, String style, boolean watermark);

    String supportAiProvider();

}
