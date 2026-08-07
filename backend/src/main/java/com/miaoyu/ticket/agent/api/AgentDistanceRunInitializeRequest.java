package com.miaoyu.ticket.agent.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/** 距离推荐初始化只接收正常消息输入，绝不接收位置资料。 */
public record AgentDistanceRunInitializeRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String clientRequestId,
        @NotBlank @Size(max = 2000) String content,
        @NotNull @Valid Context context) {
    public record Context(@NotBlank @Pattern(regexp = "^workspace$") String entry) {
    }
}
