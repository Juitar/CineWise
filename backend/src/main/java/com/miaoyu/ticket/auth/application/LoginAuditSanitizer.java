package com.miaoyu.ticket.auth.application;

/** 把网络标识转成不可逆摘要，避免登录日志保存原始 IP 和完整 User-Agent。 */
public interface LoginAuditSanitizer {

    String hashIp(String remoteAddress);

    String summarizeUserAgent(String userAgent);
}
