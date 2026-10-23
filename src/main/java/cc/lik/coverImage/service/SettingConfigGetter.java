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
    }

    @Data
    class AIConfig {
        public static final String GROUP = "ai";
        private String promptTemplate;
    }

}
