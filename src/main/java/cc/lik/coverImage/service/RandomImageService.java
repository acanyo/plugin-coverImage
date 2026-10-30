package cc.lik.coverImage.service;

import cc.lik.coverImage.image.RandomImageResponseParser;
import cc.lik.coverImage.image.RandomImageSourceResolver;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/** Owns random source selection; parsing, downloading and storage have separate responsibilities. */
@Service
@RequiredArgsConstructor
public class RandomImageService {
    private static final String API_ALCY = "https://t.alcy.cc/json?pc";
    private static final String API_BING = "https://api.xsot.cn/bing/?quality=1920x1080&mkt=zh-cn";
    private final SettingConfigGetter settings;
    private final RandomImageSourceResolver resolver;
    private final ImageTransferService transfer;

    public Mono<String> generate(Post post, String requestedSource) {
        return settings.getRandomImageConfig().flatMap(config -> {
            String source = requestedSource == null || requestedSource.isBlank()
                ? config.getDefaultSource() : requestedSource;
            if (!Set.of("alcy", "views", "custom").contains(String.valueOf(source))) {
                source = "alcy";
            }
            String url = switch (source) {
                case "views" -> API_BING;
                case "custom" -> config.getApiUrl();
                default -> API_ALCY;
            };
            if (url == null || url.isBlank()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "请先在封面图插件的随机图设置中配置自定义接口地址"));
            }
            boolean custom = source.equals("custom");
            String path = custom ? config.getJsonPath() : source.equals("views") ? "data.image" : "data.link";
            return resolver.resolve(url, custom ? config.getResponseType() : "json", path)
                .flatMap(result -> switch (result) {
                    case RandomImageResponseParser.Result.Link link -> transfer.updateFile(link.uri().toString(), post);
                    case RandomImageResponseParser.Result.File file -> transfer.updateFile(
                        Flux.defer(() -> Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(file.image().bytes()))),
                        post, file.image().filename(), file.image().mediaType());
                });
        });
    }
}
