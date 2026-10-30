package cc.lik.coverImage.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.lik.coverImage.service.RandomImageService;
import cc.lik.coverImage.service.SettingConfigGetter;
import cc.lik.coverImage.service.SettingConfigGetter.BasicConfig;
import cc.lik.coverImage.service.SettingConfigGetter.ImageProcessingConfig;
import cc.lik.coverImage.service.SettingConfigGetter.RandomImageConfig;
import cc.lik.coverImage.service.impl.ImageTransferServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/** Real HTTP + JSON/redirect recognition + native encoding + attachment upload contract. */
class RandomImagePipelineTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger imageRequests = new AtomicInteger();
    private SettingConfigGetter settings;
    private AttachmentService attachments;
    private RandomImageService service;
    private ImageTransferServiceImpl transfer;
    private RandomImageConfig random;
    private ImageProcessingConfig processing;
    private BasicConfig storage;
    private Post post;
    private FilePart uploaded;

    @BeforeEach
    void setUp() throws Exception {
        byte[] png = ImageFixtures.png(400, 200, true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            imageRequests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.createContext("/json", exchange -> {
            byte[] body = "{\"data\":{\"picture\":\"/image?token=a%2Fb\"}}".getBytes(StandardCharsets.UTF_8);
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
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        settings = mock(SettingConfigGetter.class);
        storage = new BasicConfig();
        storage.setFilePolicy("policy");
        processing = new ImageProcessingConfig();
        processing.setEnabled(true);
        processing.setMaxWidth(200);
        random = new RandomImageConfig();
        random.setDefaultSource("custom");
        random.setApiUrl(base + "/json");
        when(settings.getBasicConfig()).thenReturn(Mono.just(storage));
        when(settings.getImageProcessingConfig()).thenReturn(Mono.just(processing));
        when(settings.getRandomImageConfig()).thenReturn(Mono.just(random));
        attachments = mock(AttachmentService.class);
        var extensionClient = mock(ReactiveExtensionClient.class);
        var user = new User();
        user.setMetadata(new Metadata());
        user.getMetadata().setName("admin");
        when(extensionClient.get(User.class, "admin")).thenReturn(Mono.just(user));
        var attachment = new Attachment();
        attachment.setStatus(new Attachment.AttachmentStatus());
        attachment.getStatus().setPermalink("/upload/result.webp");
        when(attachments.upload(eq("admin"), eq("policy"), isNull(), any(FilePart.class), isNull()))
            .thenAnswer(invocation -> {
                uploaded = invocation.getArgument(3);
                return Mono.just(attachment);
            });
        var remote = new RemoteImageClient(WebClient.builder());
        transfer = new ImageTransferServiceImpl(settings, attachments, extensionClient, remote, new ImageOptimizer(new ImageDecoder()));
        service = new RandomImageService(settings, new RandomImageSourceResolver(remote, new RandomImageResponseParser(new ObjectMapper())), transfer);
        post = new Post();
        post.setSpec(new Post.PostSpec());
        post.getSpec().setOwner("admin");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void customJsonUsesSelectedImageOnceAndUploadsWebpWithMatchingMetadata() {
        assertEquals("/upload/result.webp", service.generate(post, "custom").block(Duration.ofSeconds(10)));
        assertEquals(1, imageRequests.get());
        assertTrue(uploaded.filename().endsWith(".webp"));
        assertEquals(ImageFormat.WEBP, uploaded.headers().getContentType());
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(uploadedBytes()));
    }

    @Test
    void redirectedBinaryResponseIsSavedWithoutFetchingAnotherRandomImage() {
        random.setApiUrl(base + "/redirect");
        assertEquals("/upload/result.webp", service.generate(post, "custom").block(Duration.ofSeconds(10)));
        assertEquals(1, imageRequests.get());
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(uploadedBytes()));
    }

    @Test
    void automaticGenerationUsesConfiguredDefaultCustomSource() {
        assertEquals("/upload/result.webp", service.generate(post, null).block(Duration.ofSeconds(10)));
        assertEquals(1, imageRequests.get());
    }

    @Test
    void imageProcessingRequiresStorageAndDoesNotSilentlyReturnUnconvertedUrl() {
        storage.setFilePolicy("");
        var error = assertThrows(ResponseStatusException.class,
            () -> transfer.updateFile(base + "/image", post).block(Duration.ofSeconds(10)));
        assertEquals(400, error.getStatusCode().value());
        assertEquals(0, imageRequests.get());
        verifyNoInteractions(attachments);
    }

    @Test
    void disabledProcessingAllowsExistingRemoteUrlWithoutStorage() {
        storage.setFilePolicy("");
        processing.setEnabled(false);
        assertEquals(base + "/image", transfer.updateFile(base + "/image", post).block(Duration.ofSeconds(10)));
        verifyNoInteractions(attachments);
    }

    @Test
    void redirectLoopsAreBoundedAndNeverUpload() {
        random.setApiUrl(base + "/loop");
        var error = assertThrows(ResponseStatusException.class,
            () -> service.generate(post, "custom").block(Duration.ofSeconds(10)));
        assertTrue(error.getReason().contains("重定向"));
        verifyNoInteractions(attachments);
    }

    @Test
    void missingCustomUrlIsActionable() {
        random.setApiUrl("");
        var error = assertThrows(ResponseStatusException.class,
            () -> service.generate(post, "custom").block(Duration.ofSeconds(10)));
        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("自定义接口"));
    }

    private byte[] uploadedBytes() {
        var buffer = DataBufferUtils.join(uploaded.content()).block(Duration.ofSeconds(10));
        assertNotNull(buffer);
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
