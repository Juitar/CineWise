package com.miaoyu.ticket.auth.application;

import java.time.Instant;

/** API 层只用实际到期时间设置 Cookie；JWT 原文禁止通过默认字符串输出泄露。 */
public record IssuedAccessToken(String value, Instant issuedAt, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken[value=[REDACTED], issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
