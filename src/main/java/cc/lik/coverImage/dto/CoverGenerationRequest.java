package cc.lik.coverImage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章封面生成请求。
 */
@Data
@NoArgsConstructor
public class CoverGenerationRequest {

    private String type = "randomImg";
    private String randomType = "alcy";
    private String model = "";
    private String size = "2560x1440";
    private String style = "默认";
    private String prompt = "";
}
