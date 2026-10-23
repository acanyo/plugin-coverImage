package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/**
 * 通过 Halo AI 基座生成并保存文章封面图。
 */
public interface AiImageGenerationService {

    /**
     * @param post 文章对象
     * @param modelName AI 基座中的模型资源名称；为空时使用默认模型
     * @param size 图片尺寸
     * @param style 图片风格
     * @param prompt 本次生成使用的提示词模板
     * @return 保存后的图片 URL
     */
    Mono<String> generateImage(Post post, String modelName, String size, String style,
        String prompt);

    default Mono<String> generateImage(Post post, String modelName, String size, String style) {
        return generateImage(post, modelName, size, style, "");
    }
}
