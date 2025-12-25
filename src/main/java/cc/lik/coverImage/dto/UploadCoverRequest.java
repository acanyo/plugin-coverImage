package cc.lik.coverImage.dto;

import lombok.Data;

/**
 * 上传封面图请求
 */
@Data
public class UploadCoverRequest {
    /**
     * 图片内容（URL、Base64 或 SVG）
     */
    private String imageContent;
    
    /**
     * 文章名称
     */
    private String postName;
}
