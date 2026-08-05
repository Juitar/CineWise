package com.miaoyu.ticket.agent.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST SSE 的最小请求；入口仅作为展示上下文，不携带用户、价格或票务事实。 */
public record AgentMessageStreamRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{12}$") String clientRequestId,
        @NotBlank @Size(max = 2000) String content,
        @NotNull @Valid Context context) {

    public record Context(@NotBlank @Size(max = 64) String entry) {
    }
}
