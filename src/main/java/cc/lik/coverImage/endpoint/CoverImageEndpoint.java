package cc.lik.coverImage.endpoint;

import cc.lik.coverImage.dto.CoverGenerationResponse;
import cc.lik.coverImage.dto.CoverGenerationRequest;
import cc.lik.coverImage.dto.UploadCoverRequest;
import cc.lik.coverImage.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

import java.util.Set;

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

    private static final int MAX_PROMPT_LENGTH = 8000;
    private static final int MAX_STYLE_LENGTH = 100;
    private static final String TYPE_RANDOM = "randomImg";
    private static final String TYPE_FIRST_POST = "firstPostImg";
    private static final String TYPE_CUSTOMIZE = "customizeImg";
    private static final String TYPE_AI = "aiGenerated";

    private final ReactiveExtensionClient client;
    private final ImageService imageService;

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
                    .requestBody(requestBodyBuilder().implementation(CoverGenerationRequest.class))
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
        return request.bodyToMono(CoverGenerationRequest.class)
            .switchIfEmpty(Mono.fromSupplier(() -> legacyQueryRequest(request)))
            .flatMap(this::validateRequest)
            .flatMap(generationRequest -> generateAndSetCover(postName, generationRequest))
            .flatMap(imageUrl -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CoverGenerationResponse.builder()
                    .status("success")
                    .message("封面图生成成功")
                    .imageUrl(imageUrl)
                    .build()))
            .onErrorResume(e -> {
                log.error("生成封面图失败: {}", e.getMessage(), e);
                return errorResponse(errorStatus(e), errorMessage(e));
            });
    }

    private CoverGenerationRequest legacyQueryRequest(ServerRequest request) {
        CoverGenerationRequest body = new CoverGenerationRequest();
        body.setType(queryOrBody(request, "type", body.getType()));
        body.setRandomType(queryOrBody(request, "randomType", body.getRandomType()));
        body.setModel(queryOrBody(request, "model", body.getModel()));
        body.setSize(queryOrBody(request, "size", body.getSize()));
        body.setStyle(queryOrBody(request, "style", body.getStyle()));
        body.setPrompt(queryOrBody(request, "prompt", body.getPrompt()));
        return body;
    }

    private String queryOrBody(ServerRequest request, String name, String bodyValue) {
        return request.queryParam(name)
            .filter(StringUtils::hasText)
            .orElse(bodyValue);
    }

    private Mono<CoverGenerationRequest> validateRequest(CoverGenerationRequest request) {
        if (request.getType() == null || !Set.of(TYPE_RANDOM, TYPE_FIRST_POST, TYPE_CUSTOMIZE, TYPE_AI)
            .contains(request.getType())) {
            return Mono.error(new ServerWebInputException("不支持的封面生成类型: " + request.getType()));
        }
        if (TYPE_RANDOM.equals(request.getType())
            && (request.getRandomType() == null
                || !Set.of("alcy", "views", "custom").contains(request.getRandomType()))) {
            return Mono.error(new ServerWebInputException("不支持的随机图片类型: " + request.getRandomType()));
        }
        if (request.getPrompt() != null && request.getPrompt().length() > MAX_PROMPT_LENGTH) {
            return Mono.error(new ServerWebInputException("提示词不能超过 " + MAX_PROMPT_LENGTH + " 个字符"));
        }
        if (request.getStyle() != null && request.getStyle().length() > MAX_STYLE_LENGTH) {
            return Mono.error(new ServerWebInputException("图片风格不能超过 " + MAX_STYLE_LENGTH + " 个字符"));
        }
        if (TYPE_AI.equals(request.getType()) && request.getModel() != null
            && request.getModel().length() > 253) {
            return Mono.error(new ServerWebInputException("模型名称过长"));
        }
        if (TYPE_AI.equals(request.getType()) && request.getSize() != null && !request.getSize().isBlank()
            && !request.getSize().matches("^(?:[1-9]\\d{1,4}x[1-9]\\d{1,4}|[1-9]\\d?:[1-9]\\d?|[A-Za-z][A-Za-z0-9_-]{0,31}|[1-9]\\d?[kK])$")) {
            return Mono.error(new ServerWebInputException("图片尺寸格式不正确"));
        }
        return Mono.just(request);
    }

    private Mono<String> generateAndSetCover(String postName, CoverGenerationRequest request) {
        return client.fetch(Post.class, postName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在: " + postName)))
            .flatMap(post -> {
                log.info("开始为文章[{}]生成封面图，策略: {}", post.getSpec().getTitle(), request.getType());
                Mono<String> generationMono = switch (request.getType()) {
                    case TYPE_FIRST_POST -> imageService.processFirstPostImage(post);
                    case TYPE_CUSTOMIZE -> imageService.processCustomizeImage(post);
                    case TYPE_AI -> imageService.processAIGeneratedImage(post, request.getModel(),
                        request.getSize(), request.getStyle(), request.getPrompt());
                    case TYPE_RANDOM -> imageService.processRandomImage(post, request.getRandomType());
                    default -> Mono.error(new ServerWebInputException("不支持的封面生成类型"));
                };
                return generationMono.filter(StringUtils::hasText)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "生成服务未返回图片")))
                    .flatMap(imageUrl -> client.fetch(Post.class, postName)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在: " + postName)))
                    .flatMap(latestPost -> {
                        latestPost.getSpec().setCover(imageUrl);
                        return client.update(latestPost).thenReturn(imageUrl);
                    }));
            });
    }

    private Mono<ServerResponse> errorResponse(HttpStatusCode status, String message) {
        return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CoverGenerationResponse.builder()
                .status("failed")
                .message(message == null || message.isBlank() ? "生成封面图失败" : message)
                .build());
    }

    private HttpStatusCode errorStatus(Throwable error) {
        if (error instanceof ResponseStatusException statusException) {
            return statusException.getStatusCode();
        }
        if (error instanceof DecodingException || error instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (error instanceof WebClientException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String errorMessage(Throwable error) {
        if (error instanceof ResponseStatusException statusException) {
            return statusException.getReason();
        }
        return error.getMessage();
    }

    /**
     * 上传封面图并设置到文章
     */
    private Mono<ServerResponse> uploadCover(ServerRequest request) {
        return request.bodyToMono(UploadCoverRequest.class)
            .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空")))
            .flatMap(uploadRequest -> {
                String postName = uploadRequest.getPostName();
                String imageContent = uploadRequest.getImageContent();
                if (!StringUtils.hasText(postName)) {
                    return Mono.error(new ServerWebInputException("文章名称不能为空"));
                }
                if (!StringUtils.hasText(imageContent)) {
                    return Mono.error(new ServerWebInputException("图片内容不能为空"));
                }

                log.info("收到上传封面图请求，文章: {}", postName);
                
                return client.fetch(Post.class, postName)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在: " + postName)))
                    .flatMap(post -> imageService.uploadCoverImage(imageContent, post)
                        .flatMap(imageUrl -> {
                            // 重新获取最新的 Post 对象，设置封面
                            return client.fetch(Post.class, postName)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在: " + postName)))
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
                return ServerResponse.status(errorStatus(e))
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("上传失败: " + errorMessage(e));
            });
    }
}
