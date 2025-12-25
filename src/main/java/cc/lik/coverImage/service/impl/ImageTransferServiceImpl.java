package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.core.extension.User;

import java.util.function.BiConsumer;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTransferServiceImpl implements ImageTransferService {
    private final SettingConfigGetter settingConfigGetter;
    private final AttachmentService attachmentService;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveExtensionClient client;
    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<String> updateFile(String picUrl, Post post) {
        return getCurrentUser(post.getSpec().getOwner())
            .flatMap(user -> {
                log.info("用户[{}]开始处理图片: {}", user.getMetadata().getName(), picUrl);
                return settingConfigGetter.getBasicConfig()
                    .switchIfEmpty(Mono.error(new RuntimeException("无法获取基本配置")))
                    .flatMap(config -> {
                        // 检查配置是否完整
                        if (config.getFilePolicy() == null || config.getFilePolicy().isEmpty()) {
                            log.warn("未配置附件存储策略，直接使用原始URL: {}", picUrl);
                            return Mono.just(picUrl);
                        }

                        // 检查是否为图片URL，如果不是标准图片扩展名也尝试下载
                        boolean isStandardImageUrl = isImageUrl(picUrl);
                        log.info("URL是否为标准图片格式: {}, URL: {}", isStandardImageUrl, picUrl);

                        // 从URL提取文件名，如果没有扩展名则添加.jpg
                        String originalFileName = getFileName(picUrl);
                        if (!originalFileName.contains(".")) {
                            originalFileName = originalFileName + ".jpg";
                        }
                        final String fileName = originalFileName;
                        final MediaType mediaType = getMediaType(fileName);

                        log.info("开始下载图片，文件名: {}, 媒体类型: {}", fileName, mediaType);

                        return downloadImage(webClientBuilder.build(), picUrl)
                            .flatMap(dataBufferFlux -> {
                                var file = new SimpleFilePart(fileName, dataBufferFlux, mediaType);
                                log.info("开始上传图片到存储，策略: {}, 分组: {}", config.getFilePolicy(), config.getFileGroup());
                                return attachmentService.upload(user.getMetadata().getName(), config.getFilePolicy(), config.getFileGroup(), file, null)
                                    .subscribeOn(Schedulers.boundedElastic());
                            })
                            .handle(uploadReturn())
                            .onErrorResume(e -> {
                                log.error("图片转存失败，使用原始URL: {}, 错误: {}", picUrl, e.getMessage(), e);
                                return Mono.just(picUrl);
                            });
                    });
            });
    }

    private BiConsumer<Attachment, SynchronousSink<String>> uploadReturn() {
        return (uploadedAttachment, sink) -> {
            if (uploadedAttachment != null && uploadedAttachment.getMetadata() != null
                && uploadedAttachment.getMetadata().getAnnotations() != null) {
                String uploadedUri =
                    uploadedAttachment.getMetadata().getAnnotations().get("storage.halo.run/uri");
                log.info("图片上传成功: {}", uploadedUri);
                sink.next(uploadedUri);
            } else {
                sink.error(new IllegalStateException("图片上传成功但无法获取URI"));
            }
        };
    }

    @Override
    public Mono<String> updateFile(Flux<DataBuffer> dataBufferFlux, Post post, String filename, MediaType mediaType) {
        return getCurrentUser(post.getSpec().getOwner())
            .flatMap(user -> {
                log.info("用户[{}]开始处理图片数据流: {}", user.getMetadata().getName(), filename);
                return settingConfigGetter.getBasicConfig()
                    .switchIfEmpty(Mono.error(new RuntimeException("无法获取基本配置")))
                    .flatMap(config -> {
                        // 检查配置是否完整
                        if (config.getFilePolicy() == null || config.getFilePolicy().isEmpty()) {
                            log.warn("未配置附件存储策略，请在插件设置中配置");
                            return Mono.error(new RuntimeException("请先在插件设置中配置附件存储策略"));
                        }
                        
                        var file = new SimpleFilePart(filename, dataBufferFlux, mediaType);
                        log.info("开始上传图片到存储，策略: {}, 分组: {}", config.getFilePolicy(), config.getFileGroup());
                        return attachmentService.upload(user.getMetadata().getName(), config.getFilePolicy(), config.getFileGroup(), file, null)
                            .subscribeOn(Schedulers.boundedElastic());
                    })
                    .handle(uploadReturn())
                    .onErrorResume(e -> {
                        log.error("图片处理失败: {}", e.getMessage());
                        return Mono.error(e);
                    });
            });
    }

    private Mono<User> getCurrentUser(String userName) {
        return client.get(User.class, userName)
            .doOnError(e -> log.error("获取用户[{}]信息失败: {}", userName, e.getMessage()));
    }

    private boolean isImageUrl(String url) {
        return url != null && IMAGE_URL_PATTERN.matcher(url).matches();
    }

    private String getFileName(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    private MediaType getMediaType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (fileName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        } else if (fileName.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private Mono<Flux<DataBuffer>> downloadImage(WebClient webClient, String url) {
        return Mono.just(webClient.get()
            .uri(url)
            .header("User-Agent", "curl/8.12.1")
            .header("Accept", "*/*")
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                    log.error("下载图片失败，状态码: {}", response.statusCode());
                    return response.releaseBody().then(Mono.error(new RuntimeException("下载图片失败，状态码: " + response.statusCode())));
                })
            )
            .bodyToFlux(DataBuffer.class)
            .doOnError(e -> log.error("下载图片失败: {}", e.getMessage())));
    }
} 