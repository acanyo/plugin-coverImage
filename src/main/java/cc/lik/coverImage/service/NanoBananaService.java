package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/**
 * Nano Banana AI 图片生成服务接口
 */
public interface NanoBananaService {

    /**
     * 生成 AI 图片
     *
     * @param post 文章对象
     * @return 生成的图片 URL
     */
    Mono<String> generateImage(Post post);
}
