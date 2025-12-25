package cc.lik.coverImage.endpoint;

import cc.lik.coverImage.dto.CoverGenerationResponse;
import cc.lik.coverImage.dto.UploadCoverRequest;
import cc.lik.coverImage.service.ImageService;
import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

/**
 * AI 封面图生成 API 端点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverImageEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final ImageService imageService;
    private final SettingConfigGetter settingConfigGetter;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("coverimage.lik.cc/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "coverimage.lik.cc/v1alpha1/CoverImage";
        return SpringdocRouteBuilder.route()
            .POST("generate/{postName}", this::generateCover,
                builder -> builder.operationId("GenerateCover")
                    .description("生成文章封面图")
                    .tag(tag)
                    .parameter(parameterBuilder().name("postName").description("文章名称"))
                    .parameter(parameterBuilder().name("type").description("生成类型: randomImg, firstPostImg, customizeImg, aiGenerated"))
                    .parameter(parameterBuilder().name("model").description("AI 模型"))
                    .parameter(parameterBuilder().name("size").description("图片尺寸"))
                    .parameter(parameterBuilder().name("style").description("图片风格"))
                    .response(responseBuilder().implementation(CoverGenerationResponse.class)))
            .POST("upload", this::uploadCover,
                builder -> builder.operationId("UploadCover")
                    .description("上传封面图并设置到文章")
                    .tag(tag)
                    .requestBody(requestBodyBuilder().implementation(UploadCoverRequest.class))
                    .response(responseBuilder().implementation(String.class)))
            .build();
    }

    /**
     * 触发生成封面图
     */
    private Mono<ServerResponse> generateCover(ServerRequest request) {
        String postName = request.pathVariable("postName");
        String type = request.queryParam("type").orElse("randomImg");
        String model = request.queryParam("model").orElse("doubao-seedream-4.5");
        String size = request.queryParam("size").orElse("2560x1440");
        String style = request.queryParam("style").orElse("默认");
        boolean watermark = Boolean.parseBoolean(request.queryParam("watermark").orElse("false"));
        
        log.info("收到生成封面图请求，文章: {}, 类型: {}, 模型: {}, 尺寸: {}, 风格: {}, 水印: {}", 
            postName, type, model, size, style, watermark);

        // 使用 Mono 链式调用，直到生成完成才返回
        return client.fetch(Post.class, postName)
            .flatMap(post -> {
                log.info("开始为文章[{}]生成封面图，策略: {}", post.getSpec().getTitle(), type);
                
                Mono<String> generationMono = switch (type) {
                    case "firstPostImg" -> imageService.processFirstPostImage(post);
                    case "aiGenerated" -> imageService.processAIGeneratedImage(post, model, size, style, watermark);
                    default -> imageService.processRandomImage(post);
                };

                return generationMono
                    .flatMap(imageUrl -> {
                        // 重新获取最新的 Post 对象，避免版本冲突
                        return client.fetch(Post.class, postName)
                            .flatMap(latestPost -> {
                                latestPost.getSpec().setCover(imageUrl);
                                return client.update(latestPost)
                                    .thenReturn(imageUrl); // 返回生成的图片 URL
                            });
                    });
            })
            .flatMap(imageUrl -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CoverGenerationResponse.builder()
                    .status("success")
                    .message("封面图生成成功")
                    .imageUrl(imageUrl)
                    .build()))
            .onErrorResume(e -> {
                log.error("生成封面图失败: {}", e.getMessage());
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(CoverGenerationResponse.builder()
                        .status("failed")
                        .message("生成失败: " + e.getMessage())
                        .build());
            });
    }

    /**
     * 上传封面图并设置到文章
     */
    private Mono<ServerResponse> uploadCover(ServerRequest request) {
        return request.bodyToMono(UploadCoverRequest.class)
            .flatMap(uploadRequest -> {
                String postName = uploadRequest.getPostName();
                String imageContent = uploadRequest.getImageContent();
                
                log.info("收到上传封面图请求，文章: {}", postName);
                
                return client.fetch(Post.class, postName)
                    .flatMap(post -> imageService.uploadCoverImage(imageContent, post)
                        .flatMap(imageUrl -> {
                            // 重新获取最新的 Post 对象，设置封面
                            return client.fetch(Post.class, postName)
                                .flatMap(latestPost -> {
                                    latestPost.getSpec().setCover(imageUrl);
                                    return client.update(latestPost)
                                        .thenReturn(imageUrl);
                                });
                        }));
            })
            .flatMap(imageUrl -> ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(imageUrl))
            .onErrorResume(e -> {
                log.error("上传封面图失败: {}", e.getMessage());
                return ServerResponse.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("上传失败: " + e.getMessage());
            });
    }
}
