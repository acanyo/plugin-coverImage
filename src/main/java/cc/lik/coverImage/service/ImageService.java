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
     * @param defaultImageUrl 默认图片URL
     * @return 处理后的图片URL
     */
    Mono<String> processRandomImage(Post post, String defaultImageUrl);

    /**
     * 处理文章首图
     * @param post 文章对象
     * @param defaultImageUrl 默认图片URL
     * @return 处理后的图片URL
     */
    Mono<String> processFirstPostImage(Post post, String defaultImageUrl);

    /**
     * 处理自定义图片
     * @param post 文章对象
     * @param defaultImageUrl 默认图片URL
     * @return 处理后的图片URL
     */
    Mono<String> processCustomizeImage(Post post, String defaultImageUrl);
} 