package cc.lik.coverImage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cc.lik.coverImage.dto.RandomImageTestRequest;
import cc.lik.coverImage.dto.RandomImageTestResponse;
import cc.lik.coverImage.service.RandomImageTestService;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class RandomImageTestEndpoint implements CustomEndpoint {
    private final RandomImageTestService service;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.coverimage.lik.cc/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route().POST("random-image/test", this::test,
            builder -> builder.operationId("TestRandomImageSource")
                .description("测试当前表单中的随机图接口，获取并验证图片，不保存附件或修改文章")
                .tag("RandomImageV1alpha1Console")
                .requestBody(requestBodyBuilder().implementation(RandomImageTestRequest.class))
                .response(responseBuilder().implementation(RandomImageTestResponse.class))).build();
    }

    private Mono<ServerResponse> test(ServerRequest request) {
        return request.bodyToMono(RandomImageTestRequest.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空")))
            .flatMap(service::test)
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(error -> {
                var status = error instanceof ResponseStatusException response ? response.getStatusCode()
                    : error instanceof DecodingException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
                String detail = error instanceof ResponseStatusException response ? response.getReason() : "接口测试失败，请重试";
                return ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyValue(ProblemDetail.forStatusAndDetail(status, detail));
            });
    }
}
