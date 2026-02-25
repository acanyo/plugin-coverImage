package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.AIImageGenerator;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;

/**
 * 通用AI图片生成器抽象
 *
 * @author AdRainty
 * @version V1.0.0
 * @since 2026/2/24 21:30
 */

@Slf4j
public abstract class AbstractAIImageGenerator implements AIImageGenerator {

    @Resource
    protected SettingConfigGetter settingConfigGetter;
    @Resource
    protected PostContentService postContentService;
    @Resource
    protected ImageTransferService imageTransferService;
    @Resource
    protected WebClient.Builder webClientBuilder;
    @Resource
    protected ObjectMapper objectMapper;

    @Override
    public Mono<String> generateImage(Post post, String size, String style, boolean watermark) {
        log.info("开始为文章[{}]生成 AI 封面图, 尺寸: {}, 风格: {}, 水印: {}",
            post.getSpec().getTitle(), size, style, watermark);

        return settingConfigGetter.getAIConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取 AI 配置")))
            .flatMap(config ->
                buildPrompt(post, config, style)
                    .flatMap(prompt -> doGenerateImage(config, prompt, size, watermark, post))
            )
            .doOnSuccess(url -> log.info("AI 封面图生成成功: {}", url))
            .doOnError(e -> log.error("AI 封面图生成失败: {}", e.getMessage()));
    }

    protected abstract Mono<String> doGenerateImage(SettingConfigGetter.AIConfig config, String prompt, String size, boolean watermark, Post post);

    protected Mono<String> buildPrompt(Post post, SettingConfigGetter.AIConfig config, String style) {
        return postContentService.getHeadContent(post.getMetadata().getName())
            .switchIfEmpty(postContentService.getReleaseContent(post.getMetadata().getName()))
            .map(contentWrapper -> {
                String template = config.getPromptTemplate();
                String title = post.getSpec().getTitle();
                String content = contentWrapper.getRaw();

                // 限制内容长度，避免提示词过长
                if (content != null && content.length() > 1000) {
                    content = content.substring(0, 1000) + "...";
                }

                String prompt = template
                    .replace("{title}", title != null ? title : "")
                    .replace("{content}", content != null ? content : "");

                if (style != null && !style.isEmpty() && !"默认".equals(style)) {
                    prompt += "。图片风格：" + style;
                }

                log.info("构建提示词完成，长度: {}", prompt.length());
                return prompt;
            })
            .onErrorResume(e -> {
                log.warn("获取文章内容失败，使用标题作为提示词: {}", e.getMessage());
                String prompt = config.getPromptTemplate()
                    .replace("{title}", post.getSpec().getTitle())
                    .replace("{content}", "");

                if (style != null && !style.isEmpty() && !"默认".equals(style)) {
                    prompt += "。图片风格：" + style;
                }

                return Mono.just(prompt);
            });
    }

}
