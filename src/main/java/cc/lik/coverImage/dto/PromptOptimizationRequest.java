package cc.lik.coverImage.dto;

import lombok.Data;

/** Text model and cover preferences for a single prompt optimization. */
@Data
public class PromptOptimizationRequest {
    private String model = "";
    private String prompt = "";
    private String style = "默认";
    private String size = "";
}
