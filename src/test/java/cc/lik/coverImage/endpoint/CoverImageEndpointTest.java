package cc.lik.coverImage.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.service.ImageService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;

class CoverImageEndpointTest {

    private ReactiveExtensionClient extensionClient;
    private ImageService imageService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        extensionClient = mock(ReactiveExtensionClient.class);
        imageService = mock(ImageService.class);
        client = WebTestClient.bindToRouterFunction(
            new CoverImageEndpoint(extensionClient, imageService).endpoint()).build();
    }

    @Test
    void rejectsNullTypeBeforeCallingServices() {
        client.post().uri("/generate/post")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"type\":null}")
            .exchange().expectStatus().isBadRequest()
            .expectBody().jsonPath("$.status").isEqualTo("failed");
        verifyNoInteractions(extensionClient, imageService);
    }

    @Test
    void rejectsRetiredRandomProvidersAndOversizedPrompts() {
        client.post().uri("/generate/post").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("type", "randomImg", "randomType", "dmoe"))
            .exchange().expectStatus().isBadRequest();
        client.post().uri("/generate/post").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("type", "aiGenerated", "prompt", "a".repeat(8001)))
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(extensionClient, imageService);
    }

    @Test
    void missingPostReturns404() {
        when(extensionClient.fetch(Post.class, "missing")).thenReturn(Mono.empty());
        client.post().uri("/generate/missing").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("type", "randomImg"))
            .exchange().expectStatus().isNotFound()
            .expectBody().jsonPath("$.message").isEqualTo("文章不存在: missing");
        verifyNoInteractions(imageService);
    }

    @Test
    void requestBodyTakesPrecedenceAndSetsCoverWithoutUploadingAgain() {
        Post post = new Post();
        post.setSpec(new Post.PostSpec());
        post.getSpec().setTitle("test");
        when(extensionClient.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        when(extensionClient.update(post)).thenReturn(Mono.just(post));
        when(imageService.processAIGeneratedImage(post, "image-model", "16:9", "默认", "{title}"))
            .thenReturn(Mono.just("/upload/cover.png"));

        client.post().uri("/generate/post?type=randomImg")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("type", "aiGenerated", "model", "image-model",
                "size", "16:9", "prompt", "{title}"))
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.imageUrl").isEqualTo("/upload/cover.png");

        org.junit.jupiter.api.Assertions.assertEquals("/upload/cover.png", post.getSpec().getCover());
        verify(extensionClient).update(post);
        verify(imageService, never()).uploadCoverImage(any(), any());
    }

    @Test
    void emptyGenerationResultReturns502WithoutUpdatingPost() {
        Post post = new Post();
        post.setSpec(new Post.PostSpec());
        when(extensionClient.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        when(imageService.processRandomImage(post, "alcy")).thenReturn(Mono.empty());

        client.post().uri("/generate/post").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("type", "randomImg"))
            .exchange().expectStatus().isEqualTo(502);

        verify(extensionClient, never()).update(any(Post.class));
    }

    @Test
    void uploadRequiresBodyAndImageContent() {
        client.post().uri("/upload").exchange().expectStatus().isBadRequest();
        client.post().uri("/upload").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("postName", "post"))
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(extensionClient, imageService);
    }
}
