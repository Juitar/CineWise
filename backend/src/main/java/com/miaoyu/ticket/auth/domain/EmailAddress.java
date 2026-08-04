package com.miaoyu.ticket.auth.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** 邮箱统一按去除首尾空白和小写保存、查询，避免同一地址产生大小写分支。 */
public final class EmailAddress {

    private static final int MAX_LENGTH = 255;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9]"
                    + "(?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);

    private EmailAddress() {
    }

    /** 返回可用于数据库唯一键查询的规范化邮箱；非法输入由接口层映射为 101001。 */
    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email");
        }
        return normalized;
    }

    /** 对外只保留本地部分首字符和域名，避免认证响应泄露完整邮箱。 */
    public static String mask(String normalizedEmail) {
        int separator = normalizedEmail.indexOf('@');
        if (separator <= 0) {
            throw new IllegalArgumentException("invalid normalized email");
        }
        return normalizedEmail.charAt(0) + "***" + normalizedEmail.substring(separator);
    }
}
