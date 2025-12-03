package cc.lik.coverImage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Nano Banana API 响应 DTO
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NanoBananaResponse {

    /**
     * 任务ID (异步任务时返回)
     */
    @JsonProperty("task_id")
    private String taskId;

    /**
     * 创建时间戳
     */
    private Long created;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 生成的图片数据列表
     */
    private List<ImageData> data;

    /**
     * 错误信息
     */
    private Error error;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageData {
        /**
         * 图片 URL
         */
        private String url;

        /**
         * Base64 编码的图片数据
         */
        @JsonProperty("b64_json")
        private String b64Json;

        /**
         * 修正后的提示词
         */
        @JsonProperty("revised_prompt")
        private String revisedPrompt;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private String message;
        private String type;
        private String code;
    }
}
