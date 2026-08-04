package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.CurrentUserView;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** REST 身份 DTO 不复用内部 CurrentUser，明确排除完整邮箱和会话版本。 */
public record CurrentUserResponse(
        @Schema(description = "当前用户十进制字符串标识", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(description = "服务端确认的当前角色", requiredMode = Schema.RequiredMode.REQUIRED)
        RoleCode role,
        @Schema(description = "用户昵称", example = "演示用户", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,
        @Schema(
                description = "脱敏邮箱，不返回完整邮箱",
                example = "u***@cinewise.test",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String emailMasked,
        @Schema(description = "邮箱是否已验证", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean emailVerified,
        @Schema(description = "账号状态", requiredMode = Schema.RequiredMode.REQUIRED)
        AccountStatus status,
        @Schema(description = "已记录的隐私政策版本", example = "2026-08-03", requiredMode = Schema.RequiredMode.REQUIRED)
        String privacyPolicyVersion) {

    public static CurrentUserResponse from(CurrentUserView view) {
        return new CurrentUserResponse(
                view.id(),
                view.role(),
                view.nickname(),
                view.emailMasked(),
                view.emailVerified(),
                view.status(),
                view.privacyPolicyVersion());
    }
}
