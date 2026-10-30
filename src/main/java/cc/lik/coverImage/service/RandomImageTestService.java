package cc.lik.coverImage.service;

import cc.lik.coverImage.dto.RandomImageTestRequest;
import cc.lik.coverImage.dto.RandomImageTestResponse;
import cc.lik.coverImage.image.ImageDecoder;
import cc.lik.coverImage.image.ImageFile;
import cc.lik.coverImage.image.RandomImageSourceResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Fetch and decode only: this service has no article, attachment or configuration write dependency. */
@Service
@RequiredArgsConstructor
public class RandomImageTestService {
    private final RandomImageSourceResolver resolver;
    private final ImageDecoder decoder;

    public Mono<RandomImageTestResponse> test(RandomImageTestRequest input) {
        if (input.apiUrl() == null || input.apiUrl().isBlank() || input.apiUrl().length() > 8192
            || (input.jsonPath() != null && input.jsonPath().length() > 512)
            || (input.responseType() != null && input.responseType().length() > 16)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "请填写有效的接口地址，JSON 取图路径不能超过 512 个字符"));
        }
        return resolver.resolve(input.apiUrl(), input.responseType(), input.jsonPath())
            .flatMap(resolver::fetchImage)
            .flatMap(image -> Mono.fromCallable(() -> inspect(image)).subscribeOn(Schedulers.boundedElastic()));
    }

    private RandomImageTestResponse inspect(ImageFile file) {
        Integer width = null;
        Integer height = null;
        try {
            if (file.mediaType().getSubtype().equals("svg+xml")) {
                validateSvg(file.bytes());
            } else {
                var image = decoder.decode(file.bytes(), file.mediaType());
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (LinkageError error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境无法加载图片解码器", error);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "已获取文件，但无法解码为有效图片：" + error.getMessage(), error);
        }
        return new RandomImageTestResponse("测试成功，已获取到有效图片", file.mediaType().toString(),
            file.bytes().length, width, height);
    }

    private void validateSvg(byte[] bytes) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            if (!"svg".equals(document.getDocumentElement().getLocalName())) {
                throw new IOException("SVG 根元素无效");
            }
        } catch (Exception error) {
            throw new IOException("SVG 内容无效", error);
        }
    }
}
