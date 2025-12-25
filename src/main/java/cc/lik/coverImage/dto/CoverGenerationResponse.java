package cc.lik.coverImage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverGenerationResponse {
    private String status;
    private String message;
    private String imageUrl;
}
