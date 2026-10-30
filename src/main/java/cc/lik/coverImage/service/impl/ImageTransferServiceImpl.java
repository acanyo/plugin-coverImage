package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.image.ImageFile;
import cc.lik.coverImage.image.ImageFormat;
import cc.lik.coverImage.image.ImageOptimizer;
import cc.lik.coverImage.image.RemoteImageClient;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.service.SettingConfigGetter.BasicConfig;
import cc.lik.coverImage.service.SettingConfigGetter.ImageProcessingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;

/** Every stored cover, regardless of its source, passes through the same image processing step. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTransferServiceImpl implements ImageTransferService {
    private final SettingConfigGetter settings;
    private final AttachmentService attachments;
    private final ReactiveExtensionClient client;
    private final RemoteImageClient remote;
    private final ImageOptimizer optimizer;

    @Override
    public Mono<String> updateFile(String url, Post post) {
        return Mono.zip(settings.getBasicConfig(), settings.getImageProcessingConfig())
            .flatMap(config -> {
                BasicConfig storage = config.getT1();
                ImageProcessingConfig processing = config.getT2();
                if (!hasPolicy(storage)) {
                    return processing.isEnabled() ? Mono.error(missingPolicy()) : Mono.just(url);
                }
                return remote.fetch(url).flatMap(response -> {
                    MediaType type = ImageFormat.detect(response.bytes());
                    if (type == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "图片地址返回的内容不是有效图片"));
                    }
                    var file = new ImageFile(response.bytes(),
                        "cover-" + System.currentTimeMillis() + ImageFormat.extension(type), type);
                    return upload(file, post, storage, processing).onErrorResume(error -> {
                        if (processing.isEnabled()) {
                            return Mono.error(error);
                        }
                        log.warn("图片转存失败，使用原始图片地址：{}", error.getMessage());
                        return Mono.just(url);
                    });
                });
            });
    }

    @Override
    public Mono<String> updateFile(Flux<DataBuffer> content, Post post, String filename, MediaType mediaType) {
        return Mono.zip(settings.getBasicConfig(), settings.getImageProcessingConfig())
            .flatMap(config -> {
                if (!hasPolicy(config.getT1())) {
                    return Mono.error(missingPolicy());
                }
                return DataBufferUtils.join(content, RemoteImageClient.MAX_BYTES)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容不能为空")))
                    .map(buffer -> {
                        try {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            MediaType actualType = ImageFormat.detect(bytes);
                            if (actualType == null) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别上传的图片内容");
                            }
                            return new ImageFile(bytes, filename.replaceFirst("\\.[^.]+$", "")
                                + ImageFormat.extension(actualType), actualType);
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .flatMap(file -> upload(file, post, config.getT1(), config.getT2()))
                    .onErrorMap(DataBufferLimitException.class, error -> new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 16 MB", error));
            });
    }

    private Mono<String> upload(ImageFile input, Post post, BasicConfig storage, ImageProcessingConfig processing) {
        return Mono.fromCallable(() -> optimizer.optimize(input, processing))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(output -> client.get(User.class, post.getSpec().getOwner())
                .flatMap(user -> {
                    Flux<DataBuffer> bytes = Flux.defer(() -> Flux.just(
                        DefaultDataBufferFactory.sharedInstance.wrap(output.bytes())));
                    var part = new SimpleFilePart(output.filename(), bytes, output.mediaType());
                    return attachments.upload(user.getMetadata().getName(), storage.getFilePolicy(),
                        storage.getFileGroup(), part, null);
                }))
            .flatMap(attachment -> {
                String url = attachment.getStatus() == null ? null : attachment.getStatus().getPermalink();
                return url == null || url.isBlank() ? Mono.error(new IllegalStateException("图片上传成功但未返回永久链接"))
                    : Mono.just(url);
            });
    }

    private boolean hasPolicy(BasicConfig config) {
        return config.getFilePolicy() != null && !config.getFilePolicy().isBlank();
    }

    private ResponseStatusException missingPolicy() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "请先在基本设置中配置附件存储策略，图片压缩和格式转换需要上传处理后的文件");
    }
}
