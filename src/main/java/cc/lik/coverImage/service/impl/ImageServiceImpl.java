package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import cc.lik.coverImage.service.CoverImageGenerator;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

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

    private static final MediaType TEXT_JSON = MediaType.parseMediaType("text/json;charset=UTF-8");
    private static final String API_ACG = "https://www.dmoe.cc/random.php?return=json";
    private static final String API_BING = "https://api.xsot.cn/bing/?quality=1920x1080&mkt=zh-cn";
    private static final String API_4K = "https://api.52vmy.cn/api/img/tu/pc";

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
            return Objects.requireNonNull(doc.select("img").first()).attr("src");
        } catch (Exception e) {
            log.warn("解析HTML图片失败: {}", e.getMessage());
            return null;
        }
    }
} 
