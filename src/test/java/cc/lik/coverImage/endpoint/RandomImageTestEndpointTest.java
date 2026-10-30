package cc.lik.coverImage.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.dto.RandomImageTestResponse;
import cc.lik.coverImage.service.RandomImageTestService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class RandomImageTestEndpointTest {
    private RandomImageTestService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(RandomImageTestService.class);
        client = WebTestClient.bindToRouterFunction(new RandomImageTestEndpoint(service).endpoint()).build();
    }

    @Test
    void acceptsCurrentFormValuesWithoutReadingSavedSettings() {
        when(service.test(any())).thenReturn(Mono.just(new RandomImageTestResponse(
            "测试成功，已获取到有效图片", "image/png", 200, 40, 20)));
        client.post().uri("/random-image/test").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("apiUrl", "https://image.test/api", "responseType", "json", "jsonPath", "data.url",
                "defaultSource", "alcy"))
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.width").isEqualTo(40)
            .jsonPath("$.mediaType").isEqualTo("image/png");
        verify(service).test(argThat(input -> input.apiUrl().equals("https://image.test/api")
            && input.jsonPath().equals("data.url")));
    }

    @Test
    void errorsUseVerificationFormsProblemDetailContract() {
        when(service.test(any())).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "JSON 路径不存在或为空：data.url")));
        client.post().uri("/random-image/test").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("apiUrl", "https://image.test/api"))
            .exchange().expectStatus().isEqualTo(502)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.detail").isEqualTo("JSON 路径不存在或为空：data.url");
    }

    @Test
    void emptyAndMalformedBodiesReturn400() {
        client.post().uri("/random-image/test").exchange().expectStatus().isBadRequest();
        client.post().uri("/random-image/test").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{oops").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }
}
