package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.constant.ImageConstants;
import cc.lik.coverImage.dto.CoverImageRequest;
import cc.lik.coverImage.model.ImageType;
import cc.lik.coverImage.service.CodeSphereService;
import cc.lik.coverImage.service.CoverImageGenerator;
import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.util.ImageUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {
    private final SettingConfigGetter settingConfigGetter;
    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final CoverImageGenerator coverImageGenerator;
    private final CodeSphereService codeSphereService;
    private final ReactiveExtensionClient client;

    private static final MediaType TEXT_JSON = MediaType.parseMediaType("text/json;charset=UTF-8");
    private static final String API_ACG = "https://www.dmoe.cc/random.php?return=json";
    private static final String API_BING = "https://api.xsot.cn/bing/?quality=1920x1080&mkt=zh-cn";
    private static final String API_4K = "https://api.52vmy.cn/api/img/tu/pc";
    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private static final int BUFFER_SIZE = 8192;
    @Override
    public Mono<String> processRandomImage(Post post) {
        log.info("开始处理随机图片，文章标题: {}", post.getSpec().getTitle());
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取基本配置")))
            .flatMap(config -> {
                String randomType = config.getRandomType();
                log.info("获取到随机图片类型配置: {}", randomType);
                if (randomType == null || randomType.isEmpty()) {
                    log.error("随机图片类型未配置");
                    return Mono.error(new IllegalStateException("未配置随机图片类型"));
                }

                String apiUrl = switch (randomType) {
                    case "acg" -> API_ACG;
                    case "all4k" -> API_4K;
                    default -> API_BING;
                };
                log.info("选择API地址: {}", apiUrl);

                WebClient webClient = webClientBuilder.build();
                log.info("开始请求API获取图片");
                return webClient.get()
                    .uri(apiUrl)
                    .accept(TEXT_JSON)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "no-cache")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(response -> log.info("API响应数据: {}", response))
                    .flatMap(jsonStr -> {
                        try {
                            JsonNode json = objectMapper.readTree(jsonStr);
                            String imgUrl;

                            // 根据不同的 API 响应格式解析图片 URL
                            if (apiUrl.contains("52vmy") || apiUrl.contains("dmoe.cc")) {
                                if (json.get("code").asInt() != 200) {
                                    return Mono.error(new IllegalStateException("获取图片失败: " + json.get("msg").asText()));
                                }
                                imgUrl = json.has("imgurl") ? json.get("imgurl").asText() : json.get("url").asText();
                                log.info("从 {} 获取到图片URL: {}", apiUrl, imgUrl);
                            }else if (apiUrl.contains("bing")) {
                                imgUrl = json.get("data").get("image").asText();
                                log.info("从 {} 获取到图片URL: {}", apiUrl, imgUrl);
                            } else {
                                return Mono.error(new IllegalStateException("不支持的随机图片类型"));
                            }

                            // 使用带有请求头的WebClient来下载图片
                            return imageTransferService.updateFile(imgUrl,post)
                                .doOnSuccess(url -> log.info("图片转存成功: {}", url))
                                .doOnError(e -> log.error("图片转存失败: {}", e.getMessage()));
                        } catch (Exception e) {
                            log.error("解析JSON响应失败: {}", e.getMessage(), e);
                            return Mono.error(e);
                        }
                    });
            })
            .doOnError(e -> log.error("处理随机图片过程中发生错误: {}", e.getMessage(), e));
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
                return Mono.just(firstImgSrc);
            })
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取文章内容")))
            .onErrorResume(e -> {
                log.error("提取文章图片失败: {}", e.getMessage());
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
        return Mono.fromCallable(() -> {
                var uri = new URI(imageUrl);
                return new InputStreamResource(uri.toURL().openStream());
            })
            .flatMap(resource -> {
                var dataBufferFlux = DataBufferUtils.read(resource, dataBufferFactory, BUFFER_SIZE);
                return imageTransferService.updateFile(dataBufferFlux, post,
                    ImageConstants.COVER_PREFIX + System.currentTimeMillis() + ImageUtils.getFileExtension(imageUrl),
                    ImageUtils.getMediaTypeFromUrl(imageUrl));
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                log.error("Failed to upload image from URL: {}", imageUrl, e);
                return Mono.error(new RuntimeException("上传图片失败: " + e.getMessage(), e));
            });
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
    public Mono<String> processAIGeneratedImage(Post post, String model, String size, String style, boolean watermark) {
        log.info("使用 AI 生成图片策略处理文章: {}, 模型: {}, 尺寸: {}, 风格: {}, 水印: {}",
            post.getSpec().getTitle(), model, size, style, watermark);
        return codeSphereService.generateImage(post, model, size, style, watermark);
    }
} 
