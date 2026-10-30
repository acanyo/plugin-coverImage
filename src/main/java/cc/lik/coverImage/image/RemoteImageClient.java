package cc.lik.coverImage.image;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Fetch an API response or image, with bounded payloads and explicit HTTP redirects. */
@Component
public class RemoteImageClient {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private final WebClient client;

    public RemoteImageClient(WebClient.Builder builder) {
        client = builder.clone().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_BYTES)).build();
    }

    public Mono<Response> fetch(String url) {
        return Mono.defer(() -> fetch(httpUri(url), 0)).timeout(Duration.ofSeconds(60))
            .onErrorMap(error -> !(error instanceof ResponseStatusException), error ->
                new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "图片接口请求失败（超时、响应超过 16 MB 或网络异常）", error));
    }

    public static URI httpUri(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.strip());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片接口或图片地址必须是有效的 HTTP / HTTPS 地址", error);
        }
    }

    private Mono<Response> fetch(URI uri, int redirects) {
        return client.get().uri(uri)
            .header(HttpHeaders.USER_AGENT, "Halo-CoverImage/1.2")
            .header(HttpHeaders.ACCEPT, "image/*, application/json, text/plain, */*")
            .exchangeToMono(response -> {
                if (response.statusCode().is3xxRedirection()) {
                    URI location = response.headers().asHttpHeaders().getLocation();
                    if (location == null || redirects >= 5) {
                        return response.releaseBody().then(Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "图片接口重定向无效或超过 5 次")));
                    }
                    return response.releaseBody().then(Mono.defer(() ->
                        fetch(httpUri(uri.resolve(location).toString()), redirects + 1)));
                }
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.releaseBody().then(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "图片接口返回 HTTP " + response.statusCode().value())));
                }
                MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                    .map(bytes -> new Response(uri, contentType, bytes));
            });
    }

    public record Response(URI uri, MediaType contentType, byte[] bytes) {
    }
}
