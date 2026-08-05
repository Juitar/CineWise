package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.VerificationCodeGenerator;
import java.security.SecureRandom;

/** 使用加密安全随机数生成固定长度数字验证码。 */
public class SecureNumericVerificationCodeGenerator implements VerificationCodeGenerator {

    private final SecureRandom secureRandom;
    private final int digits;
    private final int bound;

    public SecureNumericVerificationCodeGenerator(SecureRandom secureRandom, int digits) {
        this.secureRandom = secureRandom;
        this.digits = digits;
        this.bound = powerOfTen(digits);
    }

    @Override
    public String generate() {
        return String.format("%0" + digits + "d", secureRandom.nextInt(bound));
    }

    private static int powerOfTen(int digits) {
        int value = 1;
        for (int index = 0; index < digits; index++) {
            value = Math.multiplyExact(value, 10);
        }
        return value;
    }
}
