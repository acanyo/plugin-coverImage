package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.service.SettingConfigGetter.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/**
 * 火山引擎配置
 *
 * @author AdRainty
 * @version V1.0.0
 * @since 2026/2/24 21:30
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class VolcengineImageGenerator extends AbstractAIImageGenerator {

    private static final String VOLC_GENERATIONS_PATH = "/images/generations";

    @Override
    protected Mono<String> doGenerateImage(SettingConfigGetter.AIConfig config, String prompt,
        String size, boolean watermark, Post post) {
        if (StringUtils.isBlank(config.getVolcApiKey())) {
            return Mono.error(new IllegalStateException("未配置 API Key"));
        }
        String actualModel =
            StringUtils.defaultIfBlank(config.getVolcModel(), "doubao-seedream-4-5-251128");
        return callVolcengineApi(config, prompt, actualModel, size)
            .flatMap(imageUrl -> imageTransferService.updateFile(imageUrl, post));
    }

    @Override
    public String supportAiProvider() {
        return "volcengine";
    }

    /**
     * 调用火山方舟 API 生成图片
     */
    private Mono<String> callVolcengineApi(AIConfig config, String prompt, String model,
        String size) {
        // 构建请求体
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "prompt", prompt,
            "size", size,
            "n", 1
        );

        String apiUrl = config.getVolcApiBaseUrl() + VOLC_GENERATIONS_PATH;
        log.info("调用火山方舟 API: {}, 模型：{}, 尺寸：{}", apiUrl, model, size);

        WebClient webClient = webClientBuilder.build();

        return webClient.post()
            .uri(apiUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getVolcApiKey().trim())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.USER_AGENT, "Halo-CoverImage-Plugin/1.1.0")
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(status -> status.is4xxClientError(), response ->
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("API 4xx 错误响应：{}", errorBody);
                        return Mono.error(
                            new IllegalStateException("API 请求失败 (4xx): " + errorBody));
                    }))
            .onStatus(status -> status.is5xxServerError(), response ->
                response.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("API 5xx 错误响应：{}", errorBody);
                        return Mono.error(
                            new IllegalStateException("API 服务器错误 (5xx): " + errorBody));
                    }))
            .bodyToMono(String.class)
            .flatMap(responseBody -> {
                try {
                    JsonNode root = objectMapper.readTree(responseBody);
                    // 解析火山方舟响应格式
                    JsonNode data = root.path("data");
                    if (data.isArray() && !data.isEmpty()) {
                        JsonNode imageUrl = data.get(0).path("url");
                        if (!imageUrl.isMissingNode() && !imageUrl.asText().isEmpty()) {
                            log.info("火山方舟生成图片成功：{}", imageUrl.asText());
                            return Mono.just(imageUrl.asText());
                        }
                    }
                    // 尝试另一种响应格式 (data 对象包含 url 列表)
                    JsonNode urlList = root.path("data").path("url_list");
                    if (urlList.isArray() && !urlList.isEmpty()) {
                        String url = urlList.get(0).asText();
                        if (!url.isEmpty()) {
                            log.info("火山方舟生成图片成功：{}", url);
                            return Mono.just(url);
                        }
                    }
                    log.error("火山方舟响应解析失败：{}", responseBody);
                    return Mono.error(new IllegalStateException("API 响应未包含图片 URL"));
                } catch (Exception e) {
                    log.error("解析火山方舟响应失败：{}", e.getMessage());
                    return Mono.error(new IllegalStateException("解析响应失败：" + e.getMessage()));
                }
            });
    }

}
