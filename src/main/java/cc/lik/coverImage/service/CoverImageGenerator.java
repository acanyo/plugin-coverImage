package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import java.io.IOException;

public interface CoverImageGenerator {
    /**
     * 生成封面图片
     *
     * @param post Post 对象
     * @return 生成的图片URL
     * @throws IOException 如果生成失败
     */
    Mono<String> generateCoverImage(Post post) throws IOException;
} 