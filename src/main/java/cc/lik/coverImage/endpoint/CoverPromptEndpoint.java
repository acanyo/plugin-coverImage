package cc.lik.coverImage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cc.lik.coverImage.dto.CoverGenerationResponse;
import cc.lik.coverImage.dto.PromptOptimizationRequest;
import cc.lik.coverImage.dto.PromptOptimizationResponse;
import cc.lik.coverImage.service.CoverPromptService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverPromptEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final CoverPromptService promptService;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("coverimage.lik.cc/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .POST("optimize-prompt/{postName}", this::optimizePrompt,
                builder -> builder.operationId("OptimizeCoverPrompt")
                    .description("通过 AI 基座的文本模型优化封面提示词")
                    .tag("coverimage.lik.cc/v1alpha1/CoverPrompt")
                    .parameter(parameterBuilder().name("postName").in(ParameterIn.PATH)
                        .required(true).description("文章名称"))
                    .requestBody(requestBodyBuilder().implementation(PromptOptimizationRequest.class))
                    .response(responseBuilder().implementation(PromptOptimizationResponse.class)))
            .build();
    }

    private Mono<ServerResponse> optimizePrompt(ServerRequest request) {
        String postName = request.pathVariable("postName");
        return request.bodyToMono(PromptOptimizationRequest.class)
            .defaultIfEmpty(new PromptOptimizationRequest())
            .flatMap(this::validate)
            .flatMap(input -> client.fetch(Post.class, postName)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在")))
                .flatMap(post -> promptService.optimize(post, input)))
            .flatMap(prompt -> ServerResponse.ok().bodyValue(new PromptOptimizationResponse(prompt)))
            .onErrorResume(error -> {
                log.warn("优化封面提示词失败: {}", error.getMessage());
                var status = error instanceof ResponseStatusException response
                    ? response.getStatusCode()
                    : error instanceof DecodingException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
                String message = error instanceof ResponseStatusException response
                    ? response.getReason() : "优化提示词失败，请重试";
                return ServerResponse.status(status).bodyValue(CoverGenerationResponse.builder()
                    .status("failed").message(message).build());
            });
    }

    private Mono<PromptOptimizationRequest> validate(PromptOptimizationRequest input) {
        if (tooLong(input.getPrompt(), 8000) || tooLong(input.getModel(), 253)
            || tooLong(input.getStyle(), 100) || tooLong(input.getSize(), 32)) {
            return Mono.error(new ServerWebInputException("提示词或模型参数过长"));
        }
        return Mono.just(input);
    }

    private boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }
}
