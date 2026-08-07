package com.miaoyu.ticket.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** C 上传至 D 后回传的结果；请求体不得包含任何位置资料。 */
public record AgentDistanceRecommendationResultRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String distanceContextId,
        @NotBlank @Pattern(regexp = "^NEAREST$") String distancePreference,
        @NotNull LocationResult locationResult) {
    public enum LocationResult { UPLOADED, DENIED, CANCELLED, FAILED }
}
