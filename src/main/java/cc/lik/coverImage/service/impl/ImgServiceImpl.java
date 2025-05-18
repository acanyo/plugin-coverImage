package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.ImgService;
import cc.lik.coverImage.service.SettingConfigGetter;
import com.drew.lang.annotations.NotNull;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImgServiceImpl implements ImgService {

    private final PostContentService postContentService;
    private final SettingConfigGetter settingConfigGetter;
    @Override
    public Mono<Void> coverImg(Post post) {
         return settingConfigGetter.getBasicConfig()
            .switchIfEmpty(Mono.error(new RuntimeException("无法获取基本配置")))
            .flatMap(config -> {
                String imgType = Optional.ofNullable(config.getImgType()).orElse("firstPostImg");
                switch (imgType) {
                    case "firstPostImg"-> {
                        return getPostContent(post.getMetadata().getName())
                            .flatMap(result -> {
                                System.out.println("Post content: " + result);
                                return Mono.empty();
                            });
                    }
                    case "randomImg"-> {
                        System.out.println("randomImg");
                        return Mono.empty();
                    }
                    case "customizeImg"-> {
                        System.out.println("customizeImg");
                        return Mono.empty();
                    }
                    default -> {
                        System.out.println("default case: " + imgType);
                        return Mono.empty();
                    }
                }
            })
            .then();
    }
    @NotNull
    private Mono<Document> getPostContent(String postName) {
        return postContentService.getReleaseContent(postName)
            .flatMap(contentWrapper -> Mono.just(Jsoup.parse(contentWrapper.getContent())))
            .onErrorResume(e -> Mono.error(() -> new ServerWebInputException("获取文章内容时出错: " + e.getMessage())));
    }


}
