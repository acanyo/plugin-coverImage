package cc.lik.coverImage.image;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class RandomImageResponseParserTest {
    private final RandomImageResponseParser parser = new RandomImageResponseParser(new ObjectMapper());
    private final URI endpoint = URI.create("https://example.com/random");

    @Test
    void detectsJsonEvenWithPlainTextOrMissingContentType() {
        for (String body : new String[]{"{\"data\":{\"url\":\"https://img.test/a.webp\"}}",
            "{\"data\":{\"link\":\"https://img.test/a.webp\"}}",
            "{\"image_url\":\"https://img.test/a.webp\"}", "\"https://img.test/a.webp\""}) {
            assertEquals(URI.create("https://img.test/a.webp"), link(body, "auto", ""));
        }
    }

    @Test
    void explicitDotBracketAndJsonPointerPathsSelectExactImage() {
        String body = "{\"data\":[{\"asset\":\"https://img.test/original.webp\",\"url\":\"https://img.test/thumb.webp\"}]}";
        for (String path : new String[]{"data[0].asset", "$.data[0].asset", "/data/0/asset"}) {
            assertEquals(URI.create("https://img.test/original.webp"), link(body, "json", path));
        }
    }

    @Test
    void rootArraysAndArraysSelectedByPathFormAnImagePool() {
        for (int n = 0; n < 10; n++) {
            URI image = link("{\"pictures\":[\"https://img.test/a.jpg\",\"https://img.test/b.jpg\"]}",
                "auto", "pictures");
            assertTrue(Set.of("https://img.test/a.jpg", "https://img.test/b.jpg").contains(image.toString()));
        }
        assertEquals(URI.create("https://img.test/a.jpg"), link("[{\"url\":\"https://img.test/a.jpg\"}]", "auto", ""));
    }

    @Test
    void duplicateUrlsAreAcceptedButAmbiguousFieldsRequirePath() {
        assertEquals(URI.create("https://img.test/a.jpg"), link(
            "{\"url\":\"https://img.test/a.jpg\",\"image\":\"https://img.test/a.jpg\"}", "auto", ""));
        var error = assertThrows(ResponseStatusException.class, () -> link(
            "{\"url\":\"https://img.test/a.jpg\",\"image\":\"https://img.test/b.jpg\"}", "auto", ""));
        assertTrue(error.getReason().contains("多个"));
    }

    @Test
    void handlesPlainTextProtocolRelativeAndRelativeImageUrls() {
        assertEquals(URI.create("https://example.com/a.png"), link(" /a.png\n", "auto", ""));
        assertEquals(URI.create("https://img.test/a.png"), link("//img.test/a.png", "url", ""));
        assertEquals(URI.create("https://example.com/a.png"), link("{\"data\":\"./a.png\"}", "auto", ""));
    }

    @Test
    void detectsImageByBytesEvenIfServerMislabelsItAsJson() throws Exception {
        var result = parser.parse(new RemoteImageClient.Response(endpoint,
            MediaType.APPLICATION_JSON, ImageFixtures.png(3, 2, false)), "auto", "");
        var image = assertInstanceOf(RandomImageResponseParser.Result.File.class, result);
        assertEquals(MediaType.IMAGE_PNG, image.image().mediaType());
        assertTrue(image.image().filename().endsWith(".png"));
    }

    @Test
    void missingPathsMalformedJsonAndHtmlAreActionableErrors() {
        for (String path : new String[]{"data.url", "/not-found"}) {
            var error = assertThrows(ResponseStatusException.class, () -> link("{}", "json", path));
            assertTrue(error.getReason().contains("路径"));
        }
        assertThrows(ResponseStatusException.class, () -> link("{oops}", "auto", ""));
        assertThrows(ResponseStatusException.class, () -> link("<html>Blocked</html>", "auto", ""));
        assertThrows(ResponseStatusException.class, () -> link("{\"code\":403,\"msg\":\"blocked\"}", "auto", ""));
        assertThrows(ResponseStatusException.class, () -> link("{}", "json", "data[*].url"));
    }

    @Test
    void disallowsNonHttpUrlsAndCredentials() {
        assertThrows(ResponseStatusException.class, () -> RemoteImageClient.httpUri("file:///tmp/image.png"));
        assertThrows(ResponseStatusException.class, () -> RemoteImageClient.httpUri("https://user:pass@example.com/a.png"));
        assertThrows(ResponseStatusException.class, () -> link("javascript:alert(1)", "url", ""));
    }

    private URI link(String body, String type, String path) {
        var result = parser.parse(new RemoteImageClient.Response(endpoint, MediaType.TEXT_PLAIN,
            body.getBytes(StandardCharsets.UTF_8)), type, path);
        return assertInstanceOf(RandomImageResponseParser.Result.Link.class, result).uri();
    }
}
