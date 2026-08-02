package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderOperationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 为订单写动作生成不含敏感数据的稳定参数摘要。 */
public final class OrderOperationParameterHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    private OrderOperationParameterHasher() {
    }

    public static String hash(OrderOperationType action, long orderId) {
        String canonicalParameters = action.name() + ":" + orderId;
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(
                    canonicalParameters.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256摘要算法", exception);
        }
    }
}
