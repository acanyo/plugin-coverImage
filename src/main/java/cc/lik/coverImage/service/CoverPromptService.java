package cc.lik.coverImage.service;

import cc.lik.coverImage.dto.PromptOptimizationRequest;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

public interface CoverPromptService {
    Mono<String> optimize(Post post, PromptOptimizationRequest request);
}
