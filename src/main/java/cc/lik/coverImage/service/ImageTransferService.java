package cc.lik.coverImage.service;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import org.springframework.http.MediaType;

public interface ImageTransferService {
    /**
     * 更新文件
     *
     * @param picUrl 图片URL
     * @param post 文章
     * @return 更新后的URL
     */
    Mono<String> updateFile(String picUrl, Post post);

    /**
     * 更新文件
     *
     * @param dataBufferFlux 图片数据流
     * @param post 文章
     * @param filename 文件名
     * @param mediaType 媒体类型
     * @return 更新后的URL
     */
    Mono<String> updateFile(Flux<DataBuffer> dataBufferFlux, Post post, String filename, MediaType mediaType);
} 