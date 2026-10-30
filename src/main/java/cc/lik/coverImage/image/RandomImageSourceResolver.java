package cc.lik.coverImage.image;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Shared source recognition for generation and read-only interface testing. */
@Component
@RequiredArgsConstructor
public class RandomImageSourceResolver {
    private final RemoteImageClient remote;
    private final RandomImageResponseParser parser;

    public Mono<RandomImageResponseParser.Result> resolve(String url, String responseType, String jsonPath) {
        return remote.fetch(url).map(response -> parser.parse(response, responseType, jsonPath));
    }

    public Mono<ImageFile> fetchImage(RandomImageResponseParser.Result result) {
        return switch (result) {
            case RandomImageResponseParser.Result.File file -> Mono.just(file.image());
            case RandomImageResponseParser.Result.Link link -> remote.fetch(link.uri().toString())
                .map(response -> {
                    var type = ImageFormat.detect(response.bytes());
                    if (type == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片地址返回的内容不是有效图片");
                    }
                    return new ImageFile(response.bytes(), "test" + ImageFormat.extension(type), type);
                });
        };
    }
}
