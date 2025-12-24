package cc.lik.coverImage.service;

import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfigGetter {
    Mono<BasicConfig> getBasicConfig();

    Mono<AIConfig> getAIConfig();

    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        private String fileGroup;
        private String filePolicy;
        private String randomType;
    }

    @Data
    class AIConfig {
        public static final String GROUP = "ai";
        private String apiBaseUrl = "https://api.djxx.club";
        private String apiKey;
        private String model = "nano-banana-2-2k";
        private String promptTemplate = "为这篇博客文章生成一张精美的封面图片。文章标题：{title}。文章内容摘要：{content}";
        private String aspectRatio = "16:9";
        private String imageSize = "";
        private Integer timeoutSeconds = 180;
        private Integer pollIntervalMs = 2000;
    }
}
