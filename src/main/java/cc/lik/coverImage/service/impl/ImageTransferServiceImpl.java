package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.service.AttachmentService;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTransferServiceImpl implements ImageTransferService {
    private final SettingConfigGetter settingConfigGetter;
    private final AttachmentService attachmentService;
    private final DataBufferFactory dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<String> updateFile(String picUrl) {
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new RuntimeException("无法获取基本配置")))
            .flatMap(config -> {
                // 检查是否需要转存
                if (!Boolean.TRUE.equals(config.getEnablePicDump())) {
                    log.info("图片转存功能未启用");
                    return Mono.just(picUrl);
                }
                if (!isImageUrl(picUrl)) {
                    log.info("不是有效的图片URL: {}", picUrl);
                    return Mono.just(picUrl);
                }

                HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(30))
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
                WebClient webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

                final String originalFileName = getFileName(picUrl);
                final MediaType originalMediaType = getMediaType(originalFileName);
                AtomicReference<String> fileNameRef = new AtomicReference<>(originalFileName);
                AtomicReference<MediaType> mediaTypeRef = new AtomicReference<>(originalMediaType);

                return downloadImage(webClient, picUrl)
                    .map(imageBytes -> {
                        fileNameRef.set(originalFileName);
                        mediaTypeRef.set(originalMediaType);
                        return imageBytes;
                    })
                    .flatMap(finalImageBytes -> {
                        Flux<DataBuffer> dataBufferFlux = Flux.just(dataBufferFactory.wrap(finalImageBytes));
                        return uploadToHalo(config.getFilePolicy(), config.getFileGroup(), 
                            fileNameRef.get(), dataBufferFlux, mediaTypeRef.get());
                    })
                    .map(attachment -> 
                        attachment.getMetadata().getAnnotations().get("storage.halo.run/uri")
                    )
                    .onErrorResume(e -> {
                        log.error("图片处理失败，使用原始URL: {}, 错误: {}", picUrl, e.getMessage());
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
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(byte[].class);
    }

    private Mono<Attachment> uploadToHalo(String policy, String group, String fileName, 
        Flux<DataBuffer> dataBufferFlux, MediaType mediaType) {
        return attachmentService.upload(policy, group, fileName, dataBufferFlux, mediaType);
    }
} 