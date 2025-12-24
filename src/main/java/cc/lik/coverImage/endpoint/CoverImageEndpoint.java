package cc.lik.coverImage.endpoint;

import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 封面图生成 API 端点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverImageEndpoint {

    private final ReactiveExtensionClient client;
    private final ImageService imageService;
    private final SettingConfigGetter settingConfigGetter;

    // 存储生成任务状态
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    /**
     * 任务状态
     */
    public static class TaskStatus {
        public String status; // pending, generating, success, failed
        public String message;
        public String imageUrl;
        public long startTime;

        public TaskStatus(String status, String message) {
            this.status = status;
            this.message = message;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * 注册路由
     */
    @Component
    public class CoverImageRouter {
        @org.springframework.context.annotation.Bean
        public RouterFunction<ServerResponse> coverImageRoutes() {
            return RouterFunctions.route()
                .POST("/apis/coverimage.lik.cc/v1alpha1/generate/{postName}", CoverImageEndpoint.this::generateCover)
                .GET("/apis/coverimage.lik.cc/v1alpha1/status/{postName}", CoverImageEndpoint.this::getStatus)
                .build();
        }
    }

    /**
     * 触发生成封面图
     */
    private Mono<ServerResponse> generateCover(ServerRequest request) {
        String postName = request.pathVariable("postName");
        log.info("收到生成封面图请求，文章: {}", postName);

        // 检查是否已有正在进行的任务
        TaskStatus existingTask = taskStatusMap.get(postName);
        if (existingTask != null && "generating".equals(existingTask.status)) {
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createResponse("generating", "正在生成中，请稍候...", null));
        }

        // 创建新任务
        taskStatusMap.put(postName, new TaskStatus("generating", "正在生成封面图..."));

        // 异步执行生成
        client.fetch(Post.class, postName)
            .flatMap(post -> {
                log.info("开始为文章[{}]生成 AI 封面图", post.getSpec().getTitle());
                return imageService.processAIGeneratedImage(post)
                    .flatMap(imageUrl -> {
                        // 更新文章封面
                        return client.fetch(Post.class, postName)
                            .flatMap(latestPost -> {
                                latestPost.getSpec().setCover(imageUrl);
                                return client.update(latestPost)
                                    .doOnSuccess(p -> {
                                        log.info("文章[{}]封面图更新成功: {}", p.getSpec().getTitle(), imageUrl);
                                        TaskStatus status = taskStatusMap.get(postName);
                                        if (status != null) {
                                            status.status = "success";
                                            status.message = "封面图生成成功";
                                            status.imageUrl = imageUrl;
                                        }
                                    });
                            });
                    });
            })
            .doOnError(e -> {
                log.error("生成封面图失败: {}", e.getMessage());
                TaskStatus status = taskStatusMap.get(postName);
                if (status != null) {
                    status.status = "failed";
                    status.message = "生成失败: " + e.getMessage();
                }
            })
            .subscribe();

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createResponse("generating", "已开始生成封面图...", null));
    }

    /**
     * 查询生成状态
     */
    private Mono<ServerResponse> getStatus(ServerRequest request) {
        String postName = request.pathVariable("postName");

        TaskStatus status = taskStatusMap.get(postName);
        if (status == null) {
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createResponse("idle", "没有进行中的任务", null));
        }

        // 清理已完成超过5分钟的任务
        if (("success".equals(status.status) || "failed".equals(status.status))
            && System.currentTimeMillis() - status.startTime > 300000) {
            taskStatusMap.remove(postName);
        }

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createResponse(status.status, status.message, status.imageUrl));
    }

    private Map<String, Object> createResponse(String status, String message, String imageUrl) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        if (imageUrl != null) {
            response.put("imageUrl", imageUrl);
        }
        return response;
    }
}
