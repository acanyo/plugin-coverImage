package cc.lik.coverImage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.dto.PromptOptimizationRequest;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.util.AiPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.app.content.ContentWrapper;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.Metadata;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class CoverPromptServiceImplTest {
    private final PostContentService contents = mock(PostContentService.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final ExtensionGetter getter = mock(ExtensionGetter.class);
    private final AiModelService models = mock(AiModelService.class);
    private final LanguageModel model = mock(LanguageModel.class);
    private final CoverPromptServiceImpl service = new CoverPromptServiceImpl(contents, settings,
        getter, new AiPromptBuilder());
    private Post post;

    @BeforeEach
    void setUp() {
        post = new Post();
        post.setMetadata(new Metadata());
        post.getMetadata().setName("post");
        post.setSpec(new Post.PostSpec());
        post.getSpec().setTitle("春季旅行");
        var config = new SettingConfigGetter.AIConfig();
        config.setPromptTemplate("设置中的提示词 {title}");
        when(settings.getAIConfig()).thenReturn(Mono.just(config));
        when(contents.getHeadContent("post")).thenReturn(Mono.just(ContentWrapper.builder()
            .raw("<script>private-script</script><p>山川与湖泊</p>").build()));
        when(getter.getEnabledExtension(AiModelService.class)).thenReturn(Mono.just(models));
        when(models.languageModel(any())).thenReturn(Mono.just(model));
    }

    @Test
    void usesTextModelWithCleanArticleAndCoverPreferences() {
        when(model.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(GenerateTextResult.builder().text(" 山水封面提示词 ").build()));
        var input = new PromptOptimizationRequest();
        input.setModel("text-resource");
        input.setPrompt("表现 {title}，水彩质感");
        input.setStyle("水彩画");
        input.setSize("16:9");

        assertEquals("山水封面提示词", service.optimize(post, input).block());
        var capture = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(model).generateText(capture.capture());
        assertTrue(capture.getValue().getPrompt().contains("春季旅行"));
        assertTrue(capture.getValue().getPrompt().contains("山川与湖泊"));
        assertTrue(capture.getValue().getPrompt().contains("水彩画"));
        assertTrue(capture.getValue().getPrompt().contains("16:9"));
        assertFalse(capture.getValue().getPrompt().contains("private-script"));
        verify(models).languageModel("text-resource");
        verify(models, never()).imageGenerationModel(any());
        assertNull(post.getSpec().getCover());
    }

    @Test
    void titleOnlyArticleCanUseConfiguredPromptAndDefaultModel() {
        when(contents.getHeadContent("post")).thenReturn(Mono.empty());
        when(contents.getReleaseContent("post")).thenReturn(Mono.empty());
        when(model.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(GenerateTextResult.builder().text("根据标题生成的提示词").build()));

        assertNotNull(service.optimize(post, new PromptOptimizationRequest()).block());
        var capture = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(model).generateText(capture.capture());
        assertTrue(capture.getValue().getPrompt().contains("设置中的提示词 春季旅行"));
        verify(models).languageModel(null);
    }

    @Test
    void missingDefaultTextModelHasActionableError() {
        when(models.languageModel(null))
            .thenReturn(Mono.error(new DefaultModelNotConfiguredException("languageModelName")));
        var error = assertThrows(ResponseStatusException.class,
            () -> service.optimize(post, new PromptOptimizationRequest()).block());
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("默认文本模型"));
    }

    @Test
    void blankOutputIsRejected() {
        when(model.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(GenerateTextResult.builder().text("  ").build()));
        var error = assertThrows(ResponseStatusException.class,
            () -> service.optimize(post, new PromptOptimizationRequest()).block());
        assertEquals(502, error.getStatusCode().value());
    }
}
