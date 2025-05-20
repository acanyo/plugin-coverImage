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
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.core.extension.User;

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
                        if (!isImageUrl(picUrl)) {
                            return Mono.just(picUrl);
                        }

                        final String originalFileName = getFileName(picUrl);
                        final MediaType originalMediaType = getMediaType(originalFileName);
                        
                        return downloadImage(webClientBuilder.build(), picUrl)
                            .flatMap(dataBufferFlux -> {
                                var file = new SimpleFilePart(originalFileName, dataBufferFlux, originalMediaType);
                                return attachmentService.upload(user.getMetadata().getName(), config.getFilePolicy(), config.getFileGroup(), file, null)
                                    .subscribeOn(Schedulers.boundedElastic());
                            })
                            .<String>handle((uploadedAttachment, sink) -> {
                                if (uploadedAttachment != null && uploadedAttachment.getMetadata() != null && uploadedAttachment.getMetadata().getAnnotations() != null) {
                                    String uploadedUri = uploadedAttachment.getMetadata().getAnnotations().get("storage.halo.run/uri");
                                    log.info("图片上传成功: {}", uploadedUri);
                                    sink.next(uploadedUri);
                                } else {
                                    sink.error(new IllegalStateException("图片上传成功但无法获取URI"));
                                }
                            })
                            .onErrorResume(e -> {
                                log.error("图片处理失败: {}", e.getMessage());
                                return Mono.just(picUrl);
                            });
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