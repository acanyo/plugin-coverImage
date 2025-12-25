package cc.lik.coverImage.dto;

import lombok.Data;
import java.util.List;

@Data
public class CodeSphereResponse {
    private Long created;
    private List<ImageData> data;
    
    @Data
    public static class ImageData {
        private String url;
        private String b64_json;
    }
}
