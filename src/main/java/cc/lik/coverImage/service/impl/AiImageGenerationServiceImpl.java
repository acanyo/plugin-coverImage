package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.AiImageGenerationService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.util.AiPromptBuilder;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.exception.AiFoundationException;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageGenerationServiceImpl implements AiImageGenerationService {

    private static final String DEFAULT_SIZE = "2560x1440";
    private static final DataBufferFactory DATA_BUFFER_FACTORY =
        DefaultDataBufferFactory.sharedInstance;

    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final ExtensionGetter extensionGetter;
    private final SettingConfigGetter settingConfigGetter;
    private final AiPromptBuilder aiPromptBuilder;

    @Override
    public Mono<String> generateImage(Post post, String modelName, String size, String style,
        String promptTemplate) {
        log.info("开始通过 AI 基座生成文章[{}]封面，模型: {}, 尺寸: {}, 风格: {}",
            post.getSpec().getTitle(), modelName, size, style);

        return settingConfigGetter.getAIConfig()
            .defaultIfEmpty(new SettingConfigGetter.AIConfig())
            .flatMap(config -> {
                String effectivePrompt = hasText(promptTemplate)
                    ? promptTemplate : config.getPromptTemplate();
                return buildPrompt(post, effectivePrompt, style)
                    .flatMap(prompt -> generateAndStore(post, modelName, prompt, size));
            })
            .onErrorMap(DefaultModelNotConfiguredException.class, error ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "请先在 AI 基座中配置默认图像模型，或在生成页选择模型", error))
            .onErrorMap(ModelNotFoundException.class, error ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选图像模型已不存在，请重新选择", error))
            .onErrorMap(UnsupportedModelCapabilityException.class, error ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选模型不支持文字生成图片", error))
            .onErrorMap(AiFoundationException.class, error ->
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage(), error))
            .doOnSuccess(url -> log.info("AI 基座封面图生成成功: {}", url))
            .doOnError(error -> log.error("AI 基座封面图生成失败: {}", error.getMessage()));
    }

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 基座未启用")));
    }

    private Mono<String> generateAndStore(Post post, String requestedModelName, String prompt,
        String size) {
        String modelName = hasText(requestedModelName) ? requestedModelName : null;
        var requestBuilder = GenerateImageRequest.builder()
            .prompt(prompt)
            .n(1)
            .responseFormat(ImageResponseFormat.URL);
        String imageSize = hasText(size) ? size : DEFAULT_SIZE;
        if (imageSize.contains(":")) {
            requestBuilder.aspectRatio(imageSize);
        } else {
            requestBuilder.size(imageSize);
        }
        GenerateImageRequest request = requestBuilder.build();

        return aiModelService()
            .flatMap(service -> service.imageGenerationModel(modelName))
            .flatMap(model -> model.generateImage(request))
            .flatMap(result -> storeFirstImage(result, post));
    }

    private Mono<String> storeFirstImage(GenerateImageResult result, Post post) {
        if (result == null || result.getImages() == null || result.getImages().isEmpty()) {
            return Mono.error(new IllegalStateException("AI 基座未返回图片"));
        }

        GeneratedFile file = result.getImages().getFirst();
        if (file == null) {
            return Mono.error(new IllegalStateException("AI 基座返回了空图片"));
        }
        if (file.isUrl()) {
            return imageTransferService.updateFile(file.getUrl(), post);
        }
        if (file.isBase64()) {
            return uploadBase64(file, post);
        }
        return Mono.error(new IllegalStateException("AI 基座返回的图片格式无法识别"));
    }

    private Mono<String> uploadBase64(GeneratedFile file, Post post) {
        try {
            String mediaTypeValue = hasText(file.getMediaType()) ? file.getMediaType() : "image/png";
            MediaType mediaType = MediaType.parseMediaType(mediaTypeValue);
            byte[] imageBytes = Base64.getDecoder().decode(file.getBase64());
            DataBuffer dataBuffer = DATA_BUFFER_FACTORY.wrap(imageBytes);
            Flux<DataBuffer> dataBufferFlux = Flux.just(dataBuffer);
            String filename = "ai-cover-" + System.currentTimeMillis()
                + extensionFor(mediaType.getSubtype());
            return imageTransferService.updateFile(dataBufferFlux, post, filename, mediaType);
        } catch (IllegalArgumentException error) {
            return Mono.error(new IllegalStateException("AI 基座返回的图片数据无效", error));
        }
    }

    private Mono<String> buildPrompt(Post post, String template, String style) {
        String promptTemplate = hasText(template) ? template : null;

        return postContentService.getHeadContent(post.getMetadata().getName())
            .switchIfEmpty(postContentService.getReleaseContent(post.getMetadata().getName()))
            .map(contentWrapper -> aiPromptBuilder.build(promptTemplate, post.getSpec().getTitle(),
                contentWrapper.getRaw(), style))
            .defaultIfEmpty(aiPromptBuilder.build(promptTemplate, post.getSpec().getTitle(), "", style))
            .onErrorResume(error -> {
                log.warn("获取文章内容失败，使用标题生成提示词: {}", error.getMessage());
                return Mono.just(aiPromptBuilder.build(promptTemplate, post.getSpec().getTitle(), "", style));
            });
    }

    private String extensionFor(String subtype) {
        return switch (subtype.toLowerCase()) {
            case "jpeg" -> ".jpg";
            case "webp" -> ".webp";
            case "gif" -> ".gif";
            case "bmp" -> ".bmp";
            default -> ".png";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
