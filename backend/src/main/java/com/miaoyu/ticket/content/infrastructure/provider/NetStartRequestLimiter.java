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
        // 限流器由 Spring 配置创建并注入影片、影院和排期 Provider，三类请求共享同一预算。
        this.clock = clock;
        this.requestsPerMinute = properties.requestsPerMinute();
    }

    synchronized boolean allowRequest() {
        // synchronized 保证并发请求不会同时看到未更新的窗口计数，从而突破第三方限额。
        Instant now = clock.instant();
        while (!requestTimes.isEmpty() && !requestTimes.peekFirst().plusSeconds(60).isAfter(now)) {
            requestTimes.removeFirst();
        }
        if (requestTimes.size() >= requestsPerMinute) {
            // 本地拒绝不访问网络，由上层保留最近成功快照并记录 RATE_LIMITED。
            return false;
        }
        requestTimes.addLast(now);
        return true;
    }
}
