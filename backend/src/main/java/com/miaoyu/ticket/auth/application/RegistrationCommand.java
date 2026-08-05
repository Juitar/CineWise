package com.miaoyu.ticket.auth.application;

/** 注册应用命令只包含客户端可提交字段，不接受角色、状态和服务端时间。 */
public record RegistrationCommand(
        String clientRequestId,
        String email,
        String code,
        String inviteCode,
        String password,
        String nickname,
        String privacyPolicyVersion,
        boolean privacyAccepted) {
}
