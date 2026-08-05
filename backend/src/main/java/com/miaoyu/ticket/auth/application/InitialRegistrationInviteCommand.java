package com.miaoyu.ticket.auth.application;

/** 首个培训邀请码只从私有环境配置进入应用层，不接受 HTTP 请求。 */
public record InitialRegistrationInviteCommand(
        String inviteCode, int maxUses, String validFrom, String expireTime) {

    /** 防止调试日志通过 record 默认输出邀请码明文。 */
    @Override
    public String toString() {
        return "InitialRegistrationInviteCommand[sensitiveFields=[REDACTED]]";
    }
}
