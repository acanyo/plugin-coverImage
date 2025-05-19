package cc.lik.coverImage.service;

import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfigGetter {
    Mono<BasicConfig> getBasicConfig();


    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        private String fileGroup;
        private String filePolicy;
        private String imgType;
        private String randomType;
    }
}
