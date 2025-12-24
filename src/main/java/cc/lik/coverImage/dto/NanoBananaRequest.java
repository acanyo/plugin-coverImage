package cc.lik.coverImage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Nano Banana API 请求 DTO
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NanoBananaRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 生成图片的提示词
     */
    private String prompt;

    /**
     * 响应格式: url 或 b64_json
     */
    @JsonProperty("response_format")
    private String responseFormat;

    /**
     * 图片比例: 4:3, 3:4, 16:9, 9:16, 2:3, 3:2, 1:1, 4:5, 5:4, 21:9
     */
    @JsonProperty("aspect_ratio")
    private String aspectRatio;

    /**
     * 图片尺寸: 1K, 2K, 4K (仅 nano-banana-2 支持)
     */
    @JsonProperty("image_size")
    private String imageSize;
}
