package cc.lik.coverImage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Current form values, so testing does not require saving plugin settings first. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RandomImageTestRequest(String apiUrl, String responseType, String jsonPath) {
}
