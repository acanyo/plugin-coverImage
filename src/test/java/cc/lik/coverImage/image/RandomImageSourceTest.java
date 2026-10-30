package cc.lik.coverImage.image;

import static org.junit.jupiter.api.Assertions.*;

import cc.lik.coverImage.dto.RandomImageTestRequest;
import cc.lik.coverImage.service.RandomImageTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

class RandomImageSourceTest {
    private HttpServer server;
    private String base;
    private RandomImageTestService service;
    private final AtomicInteger images = new AtomicInteger();
    private byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        png = ImageFixtures.png(40, 20, true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            images.incrementAndGet();
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.createContext("/api", exchange -> {
            byte[] body = "{\"data\":{\"asset\":\"/image\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/image");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new RandomImageTestService(new RandomImageSourceResolver(new RemoteImageClient(WebClient.builder()),
            new RandomImageResponseParser(new ObjectMapper())), new ImageDecoder());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void testsCurrentUnsavedJsonSettingsAndDownloadsResolvedImage() {
        var result = service.test(new RandomImageTestRequest(base + "/api", "auto", "data.asset"))
            .block(Duration.ofSeconds(10));
        assertNotNull(result);
        assertEquals("image/png", result.mediaType());
        assertEquals(40, result.width());
        assertEquals(20, result.height());
        assertEquals(png.length, result.byteSize());
        assertEquals(1, images.get());
    }

    @Test
    void redirectTestsDoNotFetchRandomEndpointTwice() {
        var result = service.test(new RandomImageTestRequest(base + "/redirect", "auto", ""))
            .block(Duration.ofSeconds(10));
        assertNotNull(result);
        assertEquals(40, result.width());
        assertEquals(1, images.get());
    }

    @Test
    void badJsonPathFailsBeforeDownloadingAnyImage() {
        var error = assertThrows(ResponseStatusException.class, () -> service.test(
            new RandomImageTestRequest(base + "/api", "json", "data.url")).block(Duration.ofSeconds(10)));
        assertEquals(502, error.getStatusCode().value());
        assertTrue(error.getReason().contains("路径"));
        assertEquals(0, images.get());
    }

    @Test
    void corruptImageDoesNotPassBasedOnlyOnMagicBytes() {
        png = new byte[]{(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
        var error = assertThrows(ResponseStatusException.class, () -> service.test(
            new RandomImageTestRequest(base + "/image", "auto", "")).block(Duration.ofSeconds(10)));
        assertEquals(502, error.getStatusCode().value());
        assertTrue(error.getReason().contains("无法解码"));
    }

    @Test
    void emptyAddressIsRejectedBeforeNetworkCall() {
        var error = assertThrows(ResponseStatusException.class, () -> service.test(
            new RandomImageTestRequest("", "auto", "")).block(Duration.ofSeconds(10)));
        assertEquals(400, error.getStatusCode().value());
        assertEquals(0, images.get());
    }
}
