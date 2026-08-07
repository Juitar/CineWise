package com.miaoyu.ticket.content.infrastructure.provider;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 同一应用实例内所有 NetStart 调用共享的滑动窗口限流器。
 *
 * <p>影片、影院和排期共用十次/分钟保护值，避免新增排期功能后分别计数而意外扩大对第三方的请求量。</p>
 * <p>限流窗口使用业务时钟，测试可以固定时间验证边界。</p>
 * <p>每次真实尝试都占用一个额度，短重试也不能绕过窗口。</p>
 * <p>达到上限时只返回本地拒绝，不发起网络请求。</p>
 * <p>调用方收到拒绝后会保留最近成功快照。</p>
 * <p>同步方法保护并发线程，避免多个请求同时通过检查。</p>
 * <p>该类不记录 URL、ci、Key、Cookie 或原始响应。</p>
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
