package com.miaoyu.ticket.agent.api;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SseDisconnectHandlerTest {
    @Test
    void shouldOnlyCancelHeartbeatWhenDisconnected() {
        Runnable cancelHeartbeat = Mockito.mock(Runnable.class);

        new SseDisconnectHandler(cancelHeartbeat).onDisconnected();

        verify(cancelHeartbeat).run();
    }
}
