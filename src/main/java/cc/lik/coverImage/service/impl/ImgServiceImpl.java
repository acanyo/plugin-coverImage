package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImgService;
import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImgServiceImpl implements ImgService {
    private final SettingConfigGetter settingConfigGetter;
    private final ReactiveExtensionClient client;
    private final ImageService imageService;

    @Override
    public Mono<Void> coverImg(Post post) {
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取基本配置")))
            .flatMap(config -> {
                String imgType =
                    Optional.ofNullable(post.getMetadata().getAnnotations().get("coverImgType")).orElse("randomImg");
                return switch (imgType) {
                    case "randomImg" -> imageService.processRandomImage(post);
                    case "firstPostImg" -> imageService.processFirstPostImage(post);
                    case "customizeImg" -> imageService.processCustomizeImage(post);
                    default -> Mono.error(new IllegalArgumentException("未找到对应的图片处理策略: " + imgType));
                };
            })
            .flatMap(imageUrl -> {
                // 重新获取最新的文章数据
                return client.fetch(Post.class, post.getMetadata().getName())
                    .flatMap(latestPost -> {
                        latestPost.getSpec().setCover(imageUrl);
                        return client.update(latestPost)
                            .doOnSuccess(p -> log.info("文章[{}]封面图更新成功", p.getSpec().getTitle()))
                            .then();
                    });
            })
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(throwable -> throwable.getMessage().contains("Version does not match"))
                .doBeforeRetry(retrySignal -> 
                    log.warn("更新文章封面图时发生版本冲突，正在进行第{}次重试", retrySignal.totalRetries() + 1)))
            .onErrorResume(e -> {log.error("更新文章封面图失败: {}", e.getMessage());
                return Mono.empty();
            });
    }
}
