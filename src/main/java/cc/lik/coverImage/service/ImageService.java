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
     * @param model 模型名称
     * @param size 图片尺寸
     * @param style 图片风格
     * @param watermark 是否添加水印
     * @return 处理后的图片URL
     */
    Mono<String> processAIGeneratedImage(Post post, String model, String size, String style, boolean watermark);

    /**
     * 上传封面图片
     * @param imageContent 图片内容（URL、Base64 或 SVG）
     * @param post 文章对象
     * @return 上传后的图片URL
     */
    Mono<String> uploadCoverImage(String imageContent, Post post);
} 