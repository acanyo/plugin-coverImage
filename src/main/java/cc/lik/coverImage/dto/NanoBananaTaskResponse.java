package cc.lik.coverImage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Nano Banana 任务查询响应 DTO
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NanoBananaTaskResponse {

    private String code;
    private String message;
    private TaskData data;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskData {
        /**
         * 任务ID
         */
        @JsonProperty("task_id")
        private String taskId;

        /**
         * 平台
         */
        private String platform;

        /**
         * 操作类型
         */
        private String action;

        /**
         * 任务状态: IN_PROGRESS, SUCCESS, FAILURE
         */
        private String status;

        /**
         * 失败原因
         */
        @JsonProperty("fail_reason")
        private String failReason;

        /**
         * 提交时间
         */
        @JsonProperty("submit_time")
        private Long submitTime;

        /**
         * 开始时间
         */
        @JsonProperty("start_time")
        private Long startTime;

        /**
         * 完成时间
         */
        @JsonProperty("finish_time")
        private Long finishTime;

        /**
         * 进度
         */
        private String progress;

        /**
         * 结果数据
         */
        private ResultData data;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultData {
        /**
         * 图片数据列表
         */
        private List<ImageItem> data;

        /**
         * 模型
         */
        private String model;

        /**
         * 创建时间
         */
        private Long created;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageItem {
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

    /**
     * 判断任务是否成功
     */
    public boolean isSuccess() {
        return data != null && "SUCCESS".equals(data.getStatus());
    }

    /**
     * 判断任务是否失败
     */
    public boolean isFailed() {
        return data != null && "FAILURE".equals(data.getStatus());
    }

    /**
     * 判断任务是否进行中
     */
    public boolean isInProgress() {
        return data != null && "IN_PROGRESS".equals(data.getStatus());
    }

    /**
     * 获取第一张图片的 URL
     */
    public String getFirstImageUrl() {
        if (data != null && data.getData() != null
            && data.getData().getData() != null
            && !data.getData().getData().isEmpty()) {
            return data.getData().getData().get(0).getUrl();
        }
        return null;
    }

    /**
     * 获取第一张图片的 Base64 数据
     */
    public String getFirstImageBase64() {
        if (data != null && data.getData() != null
            && data.getData().getData() != null
            && !data.getData().getData().isEmpty()) {
            return data.getData().getData().get(0).getB64Json();
        }
        return null;
    }
}
