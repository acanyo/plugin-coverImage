package cc.lik.coverImage.service;

import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfigGetter {
    Mono<BasicConfig> getBasicConfig();

    Mono<AIConfig> getAIConfig();

    Mono<ImageProcessingConfig> getImageProcessingConfig();

    Mono<RandomImageConfig> getRandomImageConfig();

    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        private String fileGroup;
        private String filePolicy;
    }

    @Data
    class AIConfig {
        public static final String GROUP = "ai";
        private String promptTemplate;
    }

    @Data
    class ImageProcessingConfig {
        public static final String GROUP = "imageProcessing";
        private boolean enabled = false;
        private String outputFormat = "webp";
        private int quality = 80;
        private int maxWidth = 1920;
    }

    @Data
    class RandomImageConfig {
        public static final String GROUP = "random";
        private String defaultSource = "alcy";
        private String apiUrl;
        private String responseType = "auto";
        private String jsonPath;
    }

}
