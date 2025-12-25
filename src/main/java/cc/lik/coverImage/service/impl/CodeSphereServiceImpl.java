package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.dto.CodeSphereRequest;
import cc.lik.coverImage.dto.CodeSphereResponse;
import cc.lik.coverImage.service.CodeSphereService;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.service.SettingConfigGetter.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSphereServiceImpl implements CodeSphereService {

    private final SettingConfigGetter settingConfigGetter;
    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String GENERATIONS_PATH = "/v1/images/generations";

    @Override
    public Mono<String> generateImage(Post post, String model, String size, String style, boolean watermark) {
        log.info("开始为文章[{}]生成 AI 封面图, 模型: {}, 尺寸: {}, 风格: {}, 水印: {}", 
            post.getSpec().getTitle(), model, size, style, watermark);

        return settingConfigGetter.getAIConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取 AI 配置")))
            .flatMap(config -> {
                if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
                    return Mono.error(new IllegalStateException("未配置 API Key"));
                }
                return buildPrompt(post, config, style)
                    .flatMap(prompt -> {
                        // 根据模型类型选择不同的 API
                        if (isGeminiModel(model)) {
                            return callGeminiApi(config, prompt, model, size, post);
                        } else {
                            return callDoubaoApi(config, prompt, model, size, watermark)
                                .flatMap(imageUrl -> imageTransferService.updateFile(imageUrl, post));
                        }
                    });
            })
            .doOnSuccess(url -> log.info("AI 封面图生成成功: {}", url))
            .doOnError(e -> log.error("AI 封面图生成失败: {}", e.getMessage()));
    }

    /**
     * 判断是否为 Gemini 模型
     */
    private boolean isGeminiModel(String model) {
        return model != null && model.startsWith("gemini");
    }

    private Mono<String> buildPrompt(Post post, AIConfig config, String style) {
        return postContentService.getHeadContent(post.getMetadata().getName())
            .switchIfEmpty(postContentService.getReleaseContent(post.getMetadata().getName()))
            .map(contentWrapper -> {
                String template = config.getPromptTemplate();
                String title = post.getSpec().getTitle();
                String content = contentWrapper.getRaw();

                // 限制内容长度，避免提示词过长
                if (content != null && content.length() > 1000) {
                    content = content.substring(0, 1000) + "...";
                }

                String prompt = template
                    .replace("{title}", title != null ? title : "")
                    .replace("{content}", content != null ? content : "");
                
                if (style != null && !style.isEmpty() && !"默认".equals(style)) {
                    prompt += "。图片风格：" + style;
                }

                log.info("构建提示词完成，长度: {}", prompt.length());
                return prompt;
            })
            .onErrorResume(e -> {
                log.warn("获取文章内容失败，使用标题作为提示词: {}", e.getMessage());
                String prompt = config.getPromptTemplate()
                    .replace("{title}", post.getSpec().getTitle())
                    .replace("{content}", "");
                
                if (style != null && !style.isEmpty() && !"默认".equals(style)) {
                    prompt += "。图片风格：" + style;
                }
                
                return Mono.just(prompt);
            });
    }

    /**
     * 调用豆包 API
     */
    private Mono<String> callDoubaoApi(AIConfig config, String prompt, String model, String size, boolean watermark) {
        // 验证图片尺寸
        if (!isValidDoubaoImageSize(size)) {
            return Mono.error(new IllegalArgumentException(
                "图片尺寸不符合要求。尺寸 " + size + " 的像素数量不足，至少需要 3,686,400 像素（约 1920x1920）"));
        }

        CodeSphereRequest request = CodeSphereRequest.builder()
            .model(model)
            .prompt(prompt)
            .size(size)
            .watermark(watermark)
            .n(1)
            .build();

        String apiUrl = config.getApiBaseUrl() + GENERATIONS_PATH;
        log.info("调用豆包 API: {}, 模型: {}, 尺寸: {}, 水印: {}", apiUrl, model, size, watermark);

        WebClient webClient = webClientBuilder.build();

        return webClient.post()
            .uri(apiUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey().trim())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.USER_AGENT, "Halo-CoverImage-Plugin/1.1.0")
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), response -> 
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("API 4xx 错误响应: {}", errorBody);
                        return Mono.error(new IllegalStateException("API 请求失败 (4xx): " + errorBody));
                    }))
            .onStatus(status -> status.is5xxServerError(), response -> 
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("API 5xx 错误响应: {}", errorBody);
                        return Mono.error(new IllegalStateException("API 服务器错误 (5xx): " + errorBody));
                    }))
            .bodyToMono(CodeSphereResponse.class)
            .map(response -> {
                if (response.getData() != null && !response.getData().isEmpty()) {
                    String url = response.getData().get(0).getUrl();
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
                throw new IllegalStateException("API 响应未包含图片 URL");
            });
    }

    /**
     * 调用 Gemini API
     */
    private Mono<String> callGeminiApi(AIConfig config, String prompt, String model, String size, Post post) {
        String aspectRatio = convertSizeToAspectRatio(size);
        boolean is2K = size != null && size.contains("2K");
        
        // 构建 Gemini 请求体
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "responseModalities", List.of("TEXT", "IMAGE")
            )
        );

        String apiUrl = config.getApiBaseUrl() + "/v1beta/models/" + model + ":generateContent?key=" + config.getApiKey().trim();
        log.info("调用 Gemini API: {}, 模型: {}, 比例: {}, 2K: {}", 
            config.getApiBaseUrl() + "/v1beta/models/" + model + ":generateContent", model, aspectRatio, is2K);

        WebClient webClient = webClientBuilder.build();

        return webClient.post()
            .uri(apiUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), response -> 
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Gemini API 4xx 错误响应: {}", errorBody);
                        return Mono.error(new IllegalStateException("Gemini API 请求失败: " + errorBody));
                    }))
            .onStatus(status -> status.is5xxServerError(), response -> 
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Gemini API 5xx 错误响应: {}", errorBody);
                        return Mono.error(new IllegalStateException("Gemini API 服务器错误: " + errorBody));
                    }))
            .bodyToMono(String.class)
            .flatMap(responseBody -> {
                try {
                    JsonNode root = objectMapper.readTree(responseBody);
                    // 解析 Gemini 响应格式
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode content = candidates.get(0).path("content");
                        JsonNode parts = content.path("parts");
                        if (parts.isArray()) {
                            for (JsonNode part : parts) {
                                JsonNode inlineData = part.path("inlineData");
                                if (!inlineData.isMissingNode()) {
                                    String base64Data = inlineData.path("data").asText();
                                    String mimeType = inlineData.path("mimeType").asText("image/png");
                                    if (base64Data != null && !base64Data.isEmpty()) {
                                        log.info("Gemini 生成图片成功，mimeType: {}, 数据长度: {}", mimeType, base64Data.length());
                                        // 将 base64 转换为 DataBuffer 并上传
                                        return uploadBase64Image(base64Data, mimeType, post);
                                    }
                                }
                            }
                        }
                    }
                    log.error("Gemini 响应解析失败: {}", responseBody);
                    return Mono.error(new IllegalStateException("Gemini API 响应未包含图片"));
                } catch (Exception e) {
                    log.error("解析 Gemini 响应失败: {}", e.getMessage());
                    return Mono.error(new IllegalStateException("解析 Gemini 响应失败: " + e.getMessage()));
                }
            });
    }

    /**
     * 上传 base64 图片
     */
    private Mono<String> uploadBase64Image(String base64Data, String mimeType, Post post) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(imageBytes);
            Flux<DataBuffer> dataBufferFlux = Flux.just(dataBuffer);
            
            // 根据 mimeType 确定文件扩展名
            String extension = switch (mimeType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                default -> ".png";
            };
            
            String filename = "gemini-cover-" + System.currentTimeMillis() + extension;
            MediaType mediaType = MediaType.parseMediaType(mimeType);
            
            log.info("开始上传 Gemini 生成的图片: {}", filename);
            return imageTransferService.updateFile(dataBufferFlux, post, filename, mediaType);
        } catch (Exception e) {
            log.error("Base64 图片解码失败: {}", e.getMessage());
            return Mono.error(new IllegalStateException("Base64 图片解码失败: " + e.getMessage()));
        }
    }

    /**
     * 将尺寸转换为 Gemini 支持的比例格式
     */
    private String convertSizeToAspectRatio(String size) {
        if (size == null || size.isEmpty()) {
            return "16:9";
        }
        
        // 如果已经是比例格式，直接返回（去掉可能的 -2K 后缀）
        if (size.contains(":")) {
            return size.replace("-2K", "");
        }
        
        try {
            String[] parts = size.split("x");
            if (parts.length != 2) {
                return "16:9";
            }
            
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            
            // 计算最大公约数
            int gcd = gcd(width, height);
            int ratioW = width / gcd;
            int ratioH = height / gcd;
            
            // 映射到 Gemini 支持的比例
            String ratio = ratioW + ":" + ratioH;
            
            // Gemini 支持的比例: 1:1 2:3 3:2 3:4 4:3 4:5 5:4 9:16 16:9 21:9
            return switch (ratio) {
                case "1:1" -> "1:1";
                case "2:3", "3:2" -> ratio;
                case "3:4", "4:3" -> ratio;
                case "4:5", "5:4" -> ratio;
                case "9:16", "16:9" -> ratio;
                case "21:9" -> "21:9";
                default -> {
                    // 近似匹配
                    double aspectRatio = (double) width / height;
                    if (aspectRatio > 2.0) yield "21:9";
                    else if (aspectRatio > 1.5) yield "16:9";
                    else if (aspectRatio > 1.2) yield "4:3";
                    else if (aspectRatio > 0.9) yield "1:1";
                    else if (aspectRatio > 0.7) yield "3:4";
                    else yield "9:16";
                }
            };
        } catch (Exception e) {
            log.warn("转换比例失败，使用默认 16:9: {}", e.getMessage());
            return "16:9";
        }
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * 验证豆包图片尺寸是否符合API要求
     */
    private boolean isValidDoubaoImageSize(String size) {
        try {
            String[] dimensions = size.split("x");
            if (dimensions.length != 2) {
                return false;
            }
            
            int width = Integer.parseInt(dimensions[0]);
            int height = Integer.parseInt(dimensions[1]);
            long totalPixels = (long) width * height;
            
            // API要求至少 3,686,400 像素
            return totalPixels >= 3686400;
        } catch (NumberFormatException e) {
            log.error("无法解析图片尺寸: {}", size);
            return false;
        }
    }
}
