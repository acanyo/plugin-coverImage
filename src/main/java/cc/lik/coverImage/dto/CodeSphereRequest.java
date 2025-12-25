package cc.lik.coverImage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeSphereRequest {
    private String model;
    private String prompt;
    private String size;
    @Builder.Default
    private int n = 1;
    @Builder.Default
    private boolean watermark = false;
}
