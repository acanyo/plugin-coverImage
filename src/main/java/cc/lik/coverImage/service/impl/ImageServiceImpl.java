package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final String RANDOM_IMAGE_API = "https://www.dmoe.cc/random.php?return=json";
    private static final MediaType TEXT_JSON = MediaType.parseMediaType("text/json;charset=UTF-8");

    @Override
    public Mono<String> processRandomImage(Post post) {
        return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取随机图片配置")))
            .flatMap(config -> {
                WebClient webClient = webClientBuilder.build();
                return webClient.get()
                    .uri(RANDOM_IMAGE_API)
                    .accept(TEXT_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(jsonStr -> {
                        try {
                            JsonNode json = objectMapper.readTree(jsonStr);
                            String code = json.get("code").asText();
                            if (!"200".equals(code)) {
                                log.error("获取随机图片失败，状态码: {}", code);
                                return Mono.error(new IllegalStateException("获取随机图片失败"));
                            }
                            String imgUrl = json.get("imgurl").asText();
                            log.info("获取随机图片成功: {}", imgUrl);
                            return imageTransferService.updateFile(imgUrl)
                                .doOnSuccess(url -> log.info("图片转存成功: {}", url))
                                .onErrorResume(e -> {
                                    log.error("图片转存失败: {}", e.getMessage());
                                    return Mono.error(e);
                                });
                        } catch (Exception e) {
                            log.error("解析JSON响应失败: {}", e.getMessage());
                            return Mono.error(e);
                        }
                    });
            });
    }

    @Override
    public Mono<String> processFirstPostImage(Post post) {
        return postContentService.getReleaseContent(post.getMetadata().getName())
            .flatMap(contentWrapper -> {
                String content = contentWrapper.getRaw();
                String rawType = contentWrapper.getRawType();
                String firstImgSrc;

                if ("markdown".equals(rawType)) {
                    firstImgSrc = extractMarkdownImage(content);
                } else {
                    firstImgSrc = extractHtmlImage(content);
                }
                if (firstImgSrc == null) {
                    return Mono.error(new IllegalStateException("未找到文章中的图片"));
                }
                return Mono.just(firstImgSrc);
            })
            .onErrorResume(e -> {
                log.error("提取文章图片失败: {}", e.getMessage());
                return Mono.error(new ServerWebInputException("获取文章内容时出错: " + e.getMessage()));
            });
    }

    @Override
    public Mono<String> processCustomizeImage(Post post) {
        // TODO: 实现自定义图片逻辑
        log.info("使用自定义图片策略处理文章: {}", post.getSpec().getTitle());
        return Mono.error(new IllegalStateException("自定义图片功能尚未实现"));
    }

    private String extractMarkdownImage(String content) {
        Pattern pattern = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(2) : null;
    }

    private String extractHtmlImage(String content) {
        try {
            Document doc = Jsoup.parse(content);
            return Objects.requireNonNull(doc.select("img").first()).attr("src");
        } catch (Exception e) {
            log.warn("解析HTML图片失败: {}", e.getMessage());
            return null;
        }
    }
} 