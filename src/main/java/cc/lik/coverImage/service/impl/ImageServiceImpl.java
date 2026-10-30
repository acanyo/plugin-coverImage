package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.constant.ImageConstants;
import cc.lik.coverImage.model.ImageType;
import cc.lik.coverImage.service.CoverImageGenerator;
import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.AiImageGenerationService;
import cc.lik.coverImage.util.ImageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import cc.lik.coverImage.service.RandomImageService;
import cc.lik.coverImage.service.SettingConfigGetter;
import run.halo.app.infra.ExternalUrlSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {
    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final RandomImageService randomImageService;
    private final SettingConfigGetter settings;
    private final ExternalUrlSupplier externalUrl;
    private final CoverImageGenerator coverImageGenerator;
    private final AiImageGenerationService aiImageGenerationService;

    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private static final int BUFFER_SIZE = 8192;

    @Override
    public Mono<String> processRandomImage(Post post, String requestedRandomType) {
        return randomImageService.generate(post, requestedRandomType);
    }

    @Override
    public Mono<String> processFirstPostImage(Post post) {
        String postName = post.getMetadata().getName();
        log.info("开始提取文章首图，文章: {}", post.getSpec().getTitle());
        
        // 优先获取草稿内容，如果没有再获取已发布内容
        return postContentService.getHeadContent(postName)
            .switchIfEmpty(postContentService.getReleaseContent(postName))
            .doOnNext(contentWrapper -> {
                log.info("获取到文章内容，类型: {}, 内容长度: {}", 
                    contentWrapper.getRawType(), 
                    contentWrapper.getRaw() != null ? contentWrapper.getRaw().length() : 0);
            })
            .flatMap(contentWrapper -> {
                String content = contentWrapper.getRaw();
                String rawType = contentWrapper.getRawType();
                
                if (content == null || content.isEmpty()) {
                    log.warn("文章内容为空");
                    return Mono.error(new IllegalStateException("文章内容为空"));
                }
                
                String firstImgSrc;
                if ("markdown".equals(rawType)) {
                    firstImgSrc = extractMarkdownImage(content);
                    log.info("Markdown 格式，提取到图片: {}", firstImgSrc);
                } else {
                    firstImgSrc = extractHtmlImage(content);
                    log.info("HTML 格式，提取到图片: {}", firstImgSrc);
                }
                
                if (firstImgSrc == null || firstImgSrc.isEmpty()) {
                    return Mono.error(new IllegalStateException("未找到文章中的图片"));
                }
                String imageUrl = firstImgSrc;
                return settings.getImageProcessingConfig().flatMap(config -> {
                    if (!config.isEnabled()) {
                        return Mono.just(imageUrl);
                    }
                    if (imageUrl.startsWith("data:")) {
                        return uploadCoverImage(imageUrl, post);
                    }
                    String absoluteUrl = imageUrl.startsWith("/")
                        ? java.net.URI.create(externalUrl.getRaw().toString()).resolve(imageUrl).toString() : imageUrl;
                    return imageTransferService.updateFile(absoluteUrl, post);
                });
            })
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取文章内容")))
            .onErrorResume(e -> {
                log.error("提取文章图片失败: {}", e.getMessage());
                if (e instanceof ResponseStatusException) {
                    return Mono.error(e);
                }
                return Mono.error(new ServerWebInputException("获取文章内容时出错: " + e.getMessage()));
            });
    }

    @Override
    public Mono<String> processCustomizeImage(Post post) {
        log.info("使用自定义图片策略处理文章: {}", post.getSpec().getTitle());
        try {
            return coverImageGenerator.generateCoverImage(post);
        } catch (IOException e) {
            log.error("生成封面图片失败", e);
            return Mono.error(e);
        }
    }

    private String extractMarkdownImage(String content) {
        Pattern pattern = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String extractHtmlImage(String content) {
        try {
            Document doc = Jsoup.parse(content);
            var imgElement = doc.selectFirst("img[src]");
            
            if (imgElement == null) {
                log.warn("HTML 中没有找到 img 标签");
                return null;
            }
            
            String src = imgElement.attr("src");
            log.info("提取到图片: {}", src);
            return src;
        } catch (Exception e) {
            log.warn("解析HTML图片失败: {}", e.getMessage());
            return null;
        }
    }
    @Override
    public Mono<String> uploadCoverImage(String imageContent, Post post) {
        if (imageContent == null || imageContent.isEmpty()) {
            return Mono.error(new IllegalArgumentException("图片内容不能为空"));
        }
        
        ImageType imageType = parseImageType(imageContent);
        log.info("上传封面图，图片类型: {}", imageType.getClass().getSimpleName());
        
        return switch (imageType) {
            case ImageType.UrlImage(String url) -> uploadFromUrl(post, url);
            case ImageType.Base64Image(String content, MediaType mediaType) ->
                uploadFromBase64(post, content, mediaType);
            case ImageType.SvgImage(String content) -> uploadSvg(post, content);
            case ImageType.LocalImage(String path) -> {
                // 本地路径已经上传过了，直接返回
                log.info("图片已是本地路径，无需重新上传: {}", path);
                yield Mono.just(path);
            }
            case ImageType.UnknownImage() -> Mono.error(new IllegalArgumentException(
                "无法识别图片格式，请确保是有效的图片URL、Base64或SVG"));
        };
    }

    private Mono<String> uploadFromUrl(Post post, String imageUrl) {
        return imageTransferService.updateFile(imageUrl, post);
    }

    private Mono<String> uploadFromBase64(Post post, String base64Content, MediaType mediaType) {
        return Mono.fromCallable(() -> {
                var parts = base64Content.split(",");
                var base64Data = parts[1];
                var imageData = Base64.getDecoder().decode(base64Data);
                return new InputStreamResource(new ByteArrayInputStream(imageData));
            })
            .flatMap(resource -> {
                var dataBufferFlux = DataBufferUtils.read(resource, dataBufferFactory, BUFFER_SIZE);
                return imageTransferService.updateFile(dataBufferFlux, post,
                    ImageConstants.COVER_PREFIX + System.currentTimeMillis() + ImageUtils.getFileExtensionFromMediaType(mediaType.toString()),
                    mediaType);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                log.error("Failed to upload image from base64", e);
                return Mono.error(new RuntimeException("上传Base64图片失败: " + e.getMessage(), e));
            });
    }

    private Mono<String> uploadSvg(Post post, String svgContent) {
        var svgInputStream = new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8));
        var svgResource = new InputStreamResource(svgInputStream);
        var dataBufferFlux = DataBufferUtils.read(svgResource, dataBufferFactory, BUFFER_SIZE);
        var filename = ImageConstants.COVER_PREFIX + System.currentTimeMillis() + ".svg";
        return imageTransferService.updateFile(dataBufferFlux, post, filename, MediaType.parseMediaType("image/svg+xml"));
    }
    private ImageType parseImageType(String content) {
        log.debug("解析图片类型，内容前50字符: {}", content.length() > 50 ? content.substring(0, 50) : content);
        
        if (content == null || content.isEmpty()) {
            log.warn("图片内容为空");
            return new ImageType.UnknownImage();
        }
        
        // 处理 URL（http 或 https）
        if (content.startsWith("http://") || content.startsWith("https://")) {
            log.debug("识别为 URL 图片");
            return new ImageType.UrlImage(content);
        }
        
        // 处理本地路径（已上传的附件，如 /upload/xxx.jpg）
        if (content.startsWith("/")) {
            log.debug("识别为本地路径图片，无需重新上传");
            return new ImageType.LocalImage(content);
        }
        
        // 处理 Base64
        if (content.startsWith("data:")) {
            var parts = content.split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException(ImageConstants.ERROR_INVALID_BASE64);
            }
            var mediaType = parts[0].split(";")[0].split(":")[1];
            log.debug("识别为 Base64 图片，媒体类型: {}", mediaType);
            return new ImageType.Base64Image(content, MediaType.parseMediaType(mediaType));
        }
        
        // 处理 SVG
        if (content.contains("<svg")) {
            log.debug("识别为 SVG 图片");
            return new ImageType.SvgImage(content);
        }
        
        log.warn("无法识别图片类型，内容: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);
        return new ImageType.UnknownImage();
    }
    @Override
    public Mono<String> processAIGeneratedImage(Post post, String model, String size, String style,
        String prompt) {
        log.info("使用 AI 生成图片策略处理文章: {}, 模型: {}, 尺寸: {}, 风格: {}",
            post.getSpec().getTitle(), model, size, style);
        return aiImageGenerationService.generateImage(post, model, size, style, prompt);
    }
} 
