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
     * @param randomType 随机图片类型
     * @return 处理后的图片URL
     */
    Mono<String> processRandomImage(Post post, String randomType);

    /**
     * 使用默认随机图片类型处理随机图片。
     *
     * <p>用于文章发布后的自动生成，此场景没有生成页面上下文。</p>
     */
    default Mono<String> processRandomImage(Post post) {
        return processRandomImage(post, "alcy");
    }

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
     * @param model 模型名称
     * @param size 图片尺寸
     * @param style 图片风格
     * @param prompt 本次生成使用的提示词模板
     * @return 处理后的图片URL
     */
    Mono<String> processAIGeneratedImage(Post post, String model, String size, String style,
        String prompt);

    default Mono<String> processAIGeneratedImage(Post post, String model, String size,
        String style) {
        return processAIGeneratedImage(post, model, size, style, "");
    }

    /**
     * 上传封面图片
     * @param imageContent 图片内容（URL、Base64 或 SVG）
     * @param post 文章对象
     * @return 上传后的图片URL
     */
    Mono<String> uploadCoverImage(String imageContent, Post post);
}
