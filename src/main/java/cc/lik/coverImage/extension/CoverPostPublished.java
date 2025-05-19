package cc.lik.coverImage.extension;

import cc.lik.coverImage.service.ImgService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.event.post.PostPublishedEvent;
import run.halo.app.extension.ExtensionClient;
import java.util.Objects;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@Component
@RequiredArgsConstructor
public class CoverPostPublished {
    private final ExtensionClient client;
    private final ImgService imgSvc;
    @Async
    @EventListener(PostPublishedEvent.class)
    public void onPostPublished(PostPublishedEvent event) {
        Mono.justOrEmpty(client.fetch(Post.class, event.getName()))
            .flatMap(post -> {
                if (post.getSpec().getCover() == null || Objects.equals(post.getSpec().getCover(),
                    "")) {
                    return imgSvc.coverImg(post);
                }
               return Mono.empty();
            })
            .doOnError(e -> System.err.println("coverImg error: " + e.getMessage()))
            .subscribe();
    }



}
