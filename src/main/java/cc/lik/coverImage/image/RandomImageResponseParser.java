package cc.lik.coverImage.image;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Response recognition and bounded JSON traversal, without executing expressions from an API. */
@Component
@RequiredArgsConstructor
public class RandomImageResponseParser {
    private static final Set<String> URL_KEYS = Set.of("url", "image", "imageurl", "img", "imgurl",
        "pic", "picurl", "picture", "pictureurl", "link", "src", "original", "originalurl", "downloadurl");
    private static final Set<String> WRAPPERS = Set.of("data", "result", "results", "images", "list", "items");
    private static final Pattern PATH_TOKEN = Pattern.compile("(?:^|\\.)([\\w-]+)|\\[(\\d+)]");
    private final ObjectMapper mapper;

    public Result parse(RemoteImageClient.Response response, String requestedType, String jsonPath) {
        String type = requestedType == null || requestedType.isBlank() ? "auto" : requestedType;
        if (!Set.of("auto", "image", "url", "json").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的接口返回类型：" + type);
        }
        MediaType imageType = ImageFormat.detect(response.bytes());
        if (type.equals("image") || (type.equals("auto") && imageType != null)) {
            if (imageType == null) {
                throw invalid("接口未返回可识别的图片文件");
            }
            return new Result.File(new ImageFile(response.bytes(),
                "random-cover-" + System.currentTimeMillis() + ImageFormat.extension(imageType), imageType));
        }
        String body = new String(response.bytes(), StandardCharsets.UTF_8).strip();
        if (body.startsWith("\uFEFF")) {
            body = body.substring(1).stripLeading();
        }
        boolean json = type.equals("json") || (type.equals("auto") && (
            response.contentType().getSubtype().toLowerCase(Locale.ROOT).contains("json")
                || body.startsWith("{") || body.startsWith("[") || body.startsWith("\"")));
        if (!json) {
            return new Result.Link(resolve(body, response.uri()));
        }
        if (response.bytes().length > 1024 * 1024) {
            throw invalid("JSON 响应超过 1 MB，请使用直接返回图片的接口");
        }
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null) {
                throw invalid("接口返回了空 JSON");
            }
            Set<URI> urls = new LinkedHashSet<>();
            if (jsonPath != null && !jsonPath.isBlank()) {
                JsonNode selected = root.at(pointer(jsonPath.strip()));
                if (selected.isMissingNode() || selected.isNull()) {
                    throw invalid("JSON 路径不存在或为空：" + jsonPath);
                }
                collect(selected, response.uri(), urls, 0, new int[]{0});
            } else {
                collect(root, response.uri(), urls, 0, new int[]{0});
            }
            if (urls.isEmpty()) {
                throw invalid("JSON 中未找到图片地址，请设置 JSON 取图路径，例如 data.url 或 data[0].url");
            }
            if (urls.size() > 1) {
                throw invalid("JSON 中找到多个不同地址，请指定 JSON 取图路径，避免误取其他图片");
            }
            return new Result.Link(urls.iterator().next());
        } catch (JsonProcessingException error) {
            throw invalid("接口内容不是有效 JSON，请检查返回类型或接口地址");
        }
    }

    private void collect(JsonNode node, URI base, Set<URI> urls, int depth, int[] visited) {
        if (depth > 10 || ++visited[0] > 1000) {
            throw invalid("JSON 结构过于复杂，请指定更精确的取图路径");
        }
        if (node.isTextual()) {
            String value = node.textValue().strip();
            if (looksLikeUrl(value)) {
                urls.add(resolve(value, base));
            }
        } else if (node.isArray()) {
            // A list of images represents a pool: pick one item once, then download that exact image.
            if (!node.isEmpty()) {
                collect(node.get(ThreadLocalRandom.current().nextInt(node.size())), base, urls, depth + 1, visited);
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String key = field.getKey().replace("_", "").toLowerCase(Locale.ROOT);
                if (URL_KEYS.contains(key) || WRAPPERS.contains(key)) {
                    collect(field.getValue(), base, urls, depth + 1, visited);
                }
            });
        }
    }

    private String pointer(String path) {
        if (path.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 取图路径不能超过 512 个字符");
        }
        if (path.startsWith("/")) {
            return path;
        }
        if (path.equals("$")) {
            return "";
        }
        String simple = path.startsWith("$.") ? path.substring(2)
            : path.startsWith("$[") ? path.substring(1) : path;
        var matcher = PATH_TOKEN.matcher(simple);
        StringBuilder pointer = new StringBuilder();
        int end = 0;
        while (matcher.find() && matcher.start() == end) {
            pointer.append('/').append(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
            end = matcher.end();
        }
        if (end != simple.length() || end == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "JSON 路径格式无效，请使用 data.url、data[0].url 或 /data/0/url");
        }
        return pointer.toString();
    }

    private URI resolve(String value, URI base) {
        if (!looksLikeUrl(value) || value.length() > 8192) {
            throw invalid("接口未返回有效图片地址，请检查返回类型和 JSON 取图路径");
        }
        try {
            return RemoteImageClient.httpUri(base.resolve(value).toString());
        } catch (IllegalArgumentException | ResponseStatusException error) {
            throw invalid("接口返回的图片地址无效");
        }
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://")
            || value.startsWith("/") || value.startsWith("./") || value.startsWith("../");
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    public sealed interface Result {
        record Link(URI uri) implements Result {
        }

        record File(ImageFile image) implements Result {
        }
    }
}
