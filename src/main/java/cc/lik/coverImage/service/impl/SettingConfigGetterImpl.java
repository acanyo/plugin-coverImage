package cc.lik.coverImage.service.impl;

import cc.lik.coverImage.service.SettingConfigGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
@RequiredArgsConstructor
public class SettingConfigGetterImpl implements SettingConfigGetter {
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<BasicConfig> getBasicConfig() {
       return settingFetcher.fetch(BasicConfig.GROUP, BasicConfig.class)
           .defaultIfEmpty(new BasicConfig());
    }
}
