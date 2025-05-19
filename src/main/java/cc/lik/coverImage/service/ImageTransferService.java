package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;

public interface ImageTransferService {
    /**
     * 更新图片URL，如果需要则转存到Halo
     * @param picUrl 原始图片URL
     * @return 转存后的URL
     */
    Mono<String> updateFile(String picUrl);
} 