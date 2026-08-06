package com.miaoyu.ticket.agent.api;

import java.util.Objects;

/** SSE 断线只清理传输资源；不得取消运行、确认动作或调用工具。 */
final class SseDisconnectHandler {
    private final Runnable cancelHeartbeat;

    SseDisconnectHandler(Runnable cancelHeartbeat) {
        this.cancelHeartbeat = Objects.requireNonNull(cancelHeartbeat, "心跳关闭器不能为空");
    }

    void onDisconnected() {
        cancelHeartbeat.run();
    }
}
