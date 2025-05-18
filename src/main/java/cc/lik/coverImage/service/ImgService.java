package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

public interface ImgService {
    Mono<Void> coverImg(Post post);
}
