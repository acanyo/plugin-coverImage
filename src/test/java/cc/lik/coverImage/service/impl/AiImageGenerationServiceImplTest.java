package cc.lik.coverImage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.service.ImageTransferService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.util.AiPromptBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.Metadata;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class AiImageGenerationServiceImplTest {

    @Test
    void usesConfiguredPromptWithTitleOnlyAndPassesAspectRatioCorrectly() {
        var contents = mock(PostContentService.class);
        var transfer = mock(ImageTransferService.class);
        var getter = mock(ExtensionGetter.class);
        var settings = mock(SettingConfigGetter.class);
        var models = mock(AiModelService.class);
        var model = mock(ImageGenerationModel.class);
        var config = new SettingConfigGetter.AIConfig();
        config.setPromptTemplate("配置提示词 {title} {content}");
        Post post = new Post();
        post.setMetadata(new Metadata());
        post.getMetadata().setName("post");
        post.setSpec(new Post.PostSpec());
        post.getSpec().setTitle("文章主题");

        when(settings.getAIConfig()).thenReturn(Mono.just(config));
        when(contents.getHeadContent("post")).thenReturn(Mono.empty());
        when(contents.getReleaseContent("post")).thenReturn(Mono.empty());
        when(getter.getEnabledExtension(AiModelService.class)).thenReturn(Mono.just(models));
        when(models.imageGenerationModel("model-resource-name")).thenReturn(Mono.just(model));
        when(model.generateImage(any(GenerateImageRequest.class))).thenReturn(Mono.just(GenerateImageResult.builder()
            .images(List.of(GeneratedFile.url("https://example.com/generated.png", "image/png")))
            .build()));
        when(transfer.updateFile("https://example.com/generated.png", post))
            .thenReturn(Mono.just("/upload/generated.png"));

        var service = new AiImageGenerationServiceImpl(contents, transfer, getter, settings,
            new AiPromptBuilder());
        assertEquals("/upload/generated.png", service.generateImage(post, "model-resource-name",
            "16:9", "默认", "").block());

        var request = ArgumentCaptor.forClass(GenerateImageRequest.class);
        verify(model).generateImage(request.capture());
        assertEquals("16:9", request.getValue().getAspectRatio());
        assertNull(request.getValue().getSize());
        assertTrue(request.getValue().getPrompt().contains("配置提示词 文章主题"));
        verify(transfer, times(1)).updateFile("https://example.com/generated.png", post);
    }
}
