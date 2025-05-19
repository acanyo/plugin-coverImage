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
import run.halo.app.core.extension.service.AttachmentService;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTransferServiceImpl implements ImageTransferService {
    private final SettingConfigGetter settingConfigGetter;
    private final AttachmentService attachmentService;
    private final WebClient.Builder webClientBuilder;
    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<String> updateFile(String picUrl) {
        log.info("开始处理图片URL进行转存（恢复非阻塞模式）: {}", picUrl);
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new RuntimeException("无法获取基本配置")))
            .flatMap(config -> {
                if (!isImageUrl(picUrl)) {
                    log.info("不是有效的图片URL，跳过转存: {}", picUrl);
                    return Mono.just(picUrl); // 返回原始URL
                }

                final String originalFileName = getFileName(picUrl);
                final MediaType originalMediaType = getMediaType(originalFileName);
                
                return downloadImage(webClientBuilder.build(), picUrl)
                    .flatMap(imageBytes -> {
                        log.info("图片下载完成，字节大小: {}", imageBytes != null ? imageBytes.length : 0);
                        if (imageBytes == null || imageBytes.length == 0) {
                             log.error("下载图片失败，获取到空字节数组");
                             return Mono.error(new IllegalStateException("下载图片失败：获取到空字节数组"));
                        }

                        log.info("尝试上传图片到Halo");
                        Flux<DataBuffer> dataBufferFlux = Flux.just(dataBufferFactory.wrap(imageBytes));
                        return uploadToHalo(config.getFilePolicy(), config.getFileGroup(), 
                            originalFileName, dataBufferFlux, originalMediaType)
                            .subscribeOn(Schedulers.boundedElastic());
                    })
                    .<String>handle((uploadedAttachment, sink) -> {
                        log.info("图片上传完成");
                        if (uploadedAttachment != null && uploadedAttachment.getMetadata() != null && uploadedAttachment.getMetadata().getAnnotations() != null) {
                             String uploadedUri = uploadedAttachment.getMetadata().getAnnotations().get("storage.halo.run/uri");
                             log.info("获取到上传后的URI: {}", uploadedUri);
                            sink.next(uploadedUri);
                        } else {
                             log.error("图片上传成功但无法获取URI或附件对象为空");
                            sink.error(new IllegalStateException("图片上传成功但无法获取URI"));
                        }
                    })
                    .onErrorResume(e -> {
                         log.error("图片处理或上传过程中发生错误: {}", e.getMessage(), e);
                         // 发生错误时返回原始URL
                         return Mono.just(picUrl);
                    });
            });
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

    private Mono<byte[]> downloadImage(WebClient webClient, String url) {
        log.info("开始下载图片: {}", url);
        return webClient.get()
            .uri(url)
            .header("User-Agent", "curl/8.12.1")
            .header("Accept", "*/*")
            .retrieve()
            // 使用 onStatus 处理错误状态码
            .onStatus(
                HttpStatusCode::isError,
                response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                    log.error("下载图片请求返回错误状态码: {}，响应体: {}", response.statusCode(), errorBody);
                    // 释放响应体资源
                    return response.releaseBody().then(Mono.error(new RuntimeException("下载图片失败，状态码: " + response.statusCode())));
                })
            )
            .bodyToMono(byte[].class)
            .doOnSuccess(bytes -> log.info("图片下载成功，大小: {} bytes", bytes.length))
            .doOnError(e -> log.error("图片下载失败: {}", e.getMessage()));
    }

    private Mono<Attachment> uploadToHalo(String policy, String group, String fileName, 
        Flux<DataBuffer> dataBufferFlux, MediaType mediaType) {
        return attachmentService.upload(policy, group, fileName, dataBufferFlux, mediaType);
    }
} 