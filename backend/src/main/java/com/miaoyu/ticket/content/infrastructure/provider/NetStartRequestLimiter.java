package com.miaoyu.ticket.content.infrastructure.provider;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 同一应用实例内所有 NetStart 调用共享的滑动窗口限流器。
 *
 * <p>影片、影院和排期共用十次/分钟保护值，避免新增排期功能后分别计数而意外扩大对第三方的请求量。</p>
 */
final class NetStartRequestLimiter {

    private final Clock clock;
    private final int requestsPerMinute;
    private final Deque<Instant> requestTimes = new ArrayDeque<>();

    NetStartRequestLimiter(NetStartProperties properties, Clock clock) {
        this.clock = clock;
        this.requestsPerMinute = properties.requestsPerMinute();
    }

    synchronized boolean allowRequest() {
        Instant now = clock.instant();
        while (!requestTimes.isEmpty() && !requestTimes.peekFirst().plusSeconds(60).isAfter(now)) {
            requestTimes.removeFirst();
        }
        if (requestTimes.size() >= requestsPerMinute) {
            return false;
        }
        requestTimes.addLast(now);
        return true;
    }
}
