package cc.lik.coverImage.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

public interface ImageTransferService {
    /**
     * 更新图片URL，如果需要则转存到Halo
     *
     * @param picUrl 原始图片URL
     * @param post
     * @return 转存后的URL
     */
    Mono<String> updateFile(String picUrl, Post post);
} 