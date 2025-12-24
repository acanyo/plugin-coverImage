package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.dto.NanoBananaRequest;
import cc.lik.coverImage.dto.NanoBananaResponse;
import cc.lik.coverImage.dto.NanoBananaTaskResponse;
import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.NanoBananaService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.service.SettingConfigGetter.AIConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;

import java.time.Duration;

/**
 * Nano Banana AI 图片生成服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NanoBananaServiceImpl implements NanoBananaService {

    private final SettingConfigGetter settingConfigGetter;
    private final PostContentService postContentService;
    private final ImageTransferService imageTransferService;
    private final WebClient.Builder webClientBuilder;

    private static final String GENERATIONS_PATH = "/v1/images/generations";
    private static final String TASKS_PATH = "/v1/images/tasks/";
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int DEFAULT_POLL_INTERVAL_MS = 2000;

    @Override
    public Mono<String> generateImage(Post post) {
        log.info("开始为文章[{}]生成 AI 封面图", post.getSpec().getTitle());

        return settingConfigGetter.getAIConfig()
            .switchIfEmpty(Mono.error(new IllegalStateException("无法获取 AI 配置")))
            .flatMap(config -> {
                if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
                    return Mono.error(new IllegalStateException("未配置 API Key"));
                }
                return buildPrompt(post, config)
                    .flatMap(prompt -> createImageTask(config, prompt))
                    .flatMap(response -> handleGenerationResponse(config, response, post));
            })
            .doOnSuccess(url -> log.info("AI 封面图生成成功: {}", url))
            .doOnError(e -> log.error("AI 封面图生成失败: {}", e.getMessage()));
    }

    /**
     * 构建提示词
     */
    private Mono<String> buildPrompt(Post post, AIConfig config) {
        return postContentService.getReleaseContent(post.getMetadata().getName())
            .map(contentWrapper -> {
                String template = config.getPromptTemplate();
                String title = post.getSpec().getTitle();
                String content = contentWrapper.getRaw();

                // 限制内容长度，避免提示词过长
                if (content != null && content.length() > 2000) {
                    content = content.substring(0, 2000) + "...";
                }

                String prompt = template
                    .replace("{title}", title != null ? title : "")
                    .replace("{content}", content != null ? content : "");

                log.info("构建提示词完成，长度: {}", prompt.length());
                return prompt;
            })
            .onErrorResume(e -> {
                log.warn("获取文章内容失败，使用标题作为提示词: {}", e.getMessage());
                String prompt = config.getPromptTemplate()
                    .replace("{title}", post.getSpec().getTitle())
                    .replace("{content}", "");
                return Mono.just(prompt);
            });
    }

    /**
     * 创建图片生成任务
     */
    private Mono<NanoBananaResponse> createImageTask(AIConfig config, String prompt) {
        // image_size 仅 nano-banana-2 模型支持
        String imageSize = null;
        if ("nano-banana-2".equals(config.getModel())
            && config.getImageSize() != null
            && !config.getImageSize().isEmpty()) {
            imageSize = config.getImageSize();
        }

        NanoBananaRequest request = NanoBananaRequest.builder()
            .model(config.getModel())
            .prompt(prompt)
            .responseFormat("url")
            .aspectRatio(config.getAspectRatio())
            .imageSize(imageSize)
            .build();

        log.info("创建图片生成任务，模型: {}, 比例: {}, 尺寸: {}",
            config.getModel(), config.getAspectRatio(), imageSize);

        WebClient webClient = webClientBuilder.build();

        return webClient.post()
            .uri(config.getApiBaseUrl() + GENERATIONS_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(NanoBananaResponse.class)
            .doOnNext(response -> log.info("创建任务响应: taskId={}", response.getTaskId()))
            .onErrorResume(e -> {
                log.error("创建图片生成任务失败: {}", e.getMessage());
                return Mono.error(new IllegalStateException("创建图片生成任务失败: " + e.getMessage()));
            });
    }

    /**
     * 处理生成响应
     */
    private Mono<String> handleGenerationResponse(AIConfig config, NanoBananaResponse response, Post post) {
        // 如果直接返回了图片数据
        if (response.getData() != null && !response.getData().isEmpty()) {
            String imageUrl = response.getData().get(0).getUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("直接获取到图片 URL: {}", imageUrl);
                return imageTransferService.updateFile(imageUrl, post);
            }
        }

        // 如果返回了任务 ID，需要轮询查询
        if (response.getTaskId() != null && !response.getTaskId().isEmpty()) {
            log.info("开始轮询任务状态，taskId: {}", response.getTaskId());
            long startTime = System.currentTimeMillis();
            return pollTaskResult(config, response.getTaskId(), post, startTime);
        }

        // 检查错误
        if (response.getError() != null) {
            return Mono.error(new IllegalStateException(
                "图片生成失败: " + response.getError().getMessage()));
        }

        return Mono.error(new IllegalStateException("未知的响应格式"));
    }

    /**
     * 轮询任务结果
     *
     * @param config    AI 配置
     * @param taskId    任务 ID
     * @param post      文章对象
     * @param startTime 轮询开始时间戳
     */
    private Mono<String> pollTaskResult(AIConfig config, String taskId, Post post, long startTime) {
        // 使用默认值避免 NPE
        int timeoutSeconds = config.getTimeoutSeconds() != null
            ? config.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        int pollIntervalMs = config.getPollIntervalMs() != null
            ? config.getPollIntervalMs() : DEFAULT_POLL_INTERVAL_MS;
        long timeoutMs = timeoutSeconds * 1000L;

        return queryTaskStatus(config, taskId)
            .flatMap(taskResponse -> {
                long elapsed = System.currentTimeMillis() - startTime;

                if (taskResponse.isSuccess()) {
                    String imageUrl = taskResponse.getFirstImageUrl();
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        log.info("任务完成，获取到图片 URL: {}", imageUrl);
                        return imageTransferService.updateFile(imageUrl, post);
                    }
                    return Mono.error(new IllegalStateException("任务成功但未返回图片 URL"));
                }

                if (taskResponse.isFailed()) {
                    String failReason = taskResponse.getData() != null
                        ? taskResponse.getData().getFailReason()
                        : "未知原因";
                    return Mono.error(new IllegalStateException("图片生成失败: " + failReason));
                }

                // 检查超时
                if (elapsed >= timeoutMs) {
                    return Mono.error(new IllegalStateException(
                        "图片生成超时，已等待 " + (elapsed / 1000) + " 秒"));
                }

                // 检查是否为进行中状态
                if (!taskResponse.isInProgress()) {
                    String status = taskResponse.getData() != null
                        ? taskResponse.getData().getStatus() : "unknown";
                    return Mono.error(new IllegalStateException("未知的任务状态: " + status));
                }

                // 继续轮询
                log.info("任务进行中，已等待 {}ms，继续轮询...", elapsed);
                return Mono.delay(Duration.ofMillis(pollIntervalMs))
                    .flatMap(ignored -> pollTaskResult(config, taskId, post, startTime));
            });
    }

    /**
     * 查询任务状态
     */
    private Mono<NanoBananaTaskResponse> queryTaskStatus(AIConfig config, String taskId) {
        WebClient webClient = webClientBuilder.build();

        return webClient.get()
            .uri(config.getApiBaseUrl() + TASKS_PATH + taskId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToMono(NanoBananaTaskResponse.class)
            .doOnNext(response -> log.debug("任务状态查询结果: status={}",
                response.getData() != null ? response.getData().getStatus() : "unknown"))
            .onErrorResume(e -> {
                log.error("查询任务状态失败: {}", e.getMessage());
                return Mono.error(new IllegalStateException("查询任务状态失败: " + e.getMessage()));
            });
    }
}
