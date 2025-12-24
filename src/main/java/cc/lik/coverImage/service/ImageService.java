package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/**
 * 图片处理服务接口
 */
public interface ImageService {
    /**
     * 处理随机图片
     * @param post 文章对象
     * @return 处理后的图片URL
     */
    Mono<String> processRandomImage(Post post);

    /**
     * 处理文章首图
     * @param post 文章对象
     * @return 处理后的图片URL
     */
    Mono<String> processFirstPostImage(Post post);

    /**
     * 处理自定义图片
     * @param post 文章对象
     * @return 处理后的图片URL
     */
    Mono<String> processCustomizeImage(Post post);

    /**
     * 处理 AI 生成图片
     * @param post 文章对象
     * @return 处理后的图片URL
     */
    Mono<String> processAIGeneratedImage(Post post);
} 