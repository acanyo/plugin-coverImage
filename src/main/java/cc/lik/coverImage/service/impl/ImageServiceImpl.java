package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {
    private final SettingConfigGetter settingConfigGetter;
    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final Random random = new Random();

    @Override
    public Mono<String> processRandomImage(Post post, String defaultImageUrl) {
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取随机图片配置")))
            .flatMap(config -> {
                String randomImgUrl = config.getRandomImgUrl();
                if (randomImgUrl == null || randomImgUrl.trim().isEmpty()) {
                    log.warn("随机图片URL未配置，使用默认图片");
                    return Mono.just(defaultImageUrl);
                }

                List<String> imageUrls = parseImageUrls(randomImgUrl);
                if (imageUrls.isEmpty()) {
                    log.warn("随机图片URL列表为空，使用默认图片");
                    return Mono.just(defaultImageUrl);
                }

                String selectedUrl = imageUrls.get(random.nextInt(imageUrls.size()));
                log.info("为文章[{}]选择随机图片: {}", post.getSpec().getTitle(), selectedUrl);

                return imageTransferService.updateFile(selectedUrl)
                    .doOnSuccess(url -> log.info("图片转存成功: {}", url))
                    .onErrorResume(e -> {
                        log.error("图片转存失败，使用原始URL: {}", e.getMessage());
                        return Mono.just(selectedUrl);
                    });
            })
            .onErrorResume(e -> {
                log.error("获取随机图片失败: {}", e.getMessage());
                return Mono.just(defaultImageUrl);
            });
    }

    @Override
    public Mono<String> processFirstPostImage(Post post, String defaultImageUrl) {
        return postContentService.getReleaseContent(post.getMetadata().getName())
            .flatMap(contentWrapper -> {
                String content = contentWrapper.getRaw();
                String rawType = contentWrapper.getRawType();
                String firstImgSrc;

                if ("markdown".equals(rawType)) {
                    firstImgSrc = extractMarkdownImage(content, defaultImageUrl);
                } else {
                    firstImgSrc = extractHtmlImage(content, defaultImageUrl);
                }
                return Mono.just(firstImgSrc);
            })
            .onErrorResume(e -> {
                log.error("提取文章图片失败: {}", e.getMessage());
                return Mono.error(new ServerWebInputException("获取文章内容时出错: " + e.getMessage()));
            });
    }

    @Override
    public Mono<String> processCustomizeImage(Post post, String defaultImageUrl) {
        // TODO: 实现自定义图片逻辑
        log.info("使用自定义图片策略处理文章: {}", post.getSpec().getTitle());
        return Mono.just(defaultImageUrl);
    }

    private List<String> parseImageUrls(String randomImgUrl) {
        return Stream.of(randomImgUrl.split("[,;\\n]"))
            .map(String::trim)
            .filter(url -> !url.isEmpty())
            .toList();
    }

    private String extractMarkdownImage(String content, String defaultImageUrl) {
        Pattern pattern = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(2) : defaultImageUrl;
    }

    private String extractHtmlImage(String content, String defaultImageUrl) {
        try {
            Document doc = Jsoup.parse(content);
            return Objects.requireNonNull(doc.select("img").first()).attr("src");
        } catch (Exception e) {
            log.warn("解析HTML图片失败，使用默认图片: {}", e.getMessage());
            return defaultImageUrl;
        }
    }
} 