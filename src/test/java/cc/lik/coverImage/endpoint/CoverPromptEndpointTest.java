package cc.lik.coverImage.endpoint;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.service.CoverPromptService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;

class CoverPromptEndpointTest {
    private final ReactiveExtensionClient extensions = mock(ReactiveExtensionClient.class);
    private final CoverPromptService prompts = mock(CoverPromptService.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(new CoverPromptEndpoint(extensions, prompts).endpoint()).build();
    }

    @Test
    void returnsPromptWithoutChangingPost() {
        Post post = new Post();
        when(extensions.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        when(prompts.optimize(eq(post), any())).thenReturn(Mono.just("优化的提示词"));
        client.post().uri("/optimize-prompt/post").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("model", "text-model", "prompt", "当前提示词"))
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.prompt").isEqualTo("优化的提示词");
        verify(extensions, never()).update(any(Post.class));
    }

    @Test
    void oversizedPromptDoesNotInvokeModel() {
        client.post().uri("/optimize-prompt/post").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("prompt", "x".repeat(8001)))
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(extensions, prompts);
    }

    @Test
    void missingPostReturns404() {
        when(extensions.fetch(Post.class, "missing")).thenReturn(Mono.empty());
        client.post().uri("/optimize-prompt/missing").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of()).exchange().expectStatus().isNotFound();
        verifyNoInteractions(prompts);
    }
}
