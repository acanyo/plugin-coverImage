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
        private String aiProvider = "volcengine";
        private String apiBaseUrl = "https://api.codesphere.chat";
        private String apiKey;
        private String model;
        private String promptTemplate = "为这篇博客文章生成一张精美的封面图片。文章标题：{title}。文章内容摘要：{content}";
        // 火山方舟配置
        private String volcApiBaseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String volcApiKey;
        private String volcModel = "doubao-seedream-4-5-251128";
    }
}
