package cc.lik.coverImage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(java.time.Duration.ofSeconds(180))
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(16 * 1024 * 1024)); // 设置为 16MB
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
} 