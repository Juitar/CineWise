package com.miaoyu.ticket.content.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.application.ContentSyncService;
import com.miaoyu.ticket.content.infrastructure.provider.NetStartProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NetStartStartupSyncRunnerTest {

    @Test
    void givenEnabledProvider_whenStartupRunnerExecutes_thenItSynchronizesExactlyOnce() {
        ContentSyncService service = mock(ContentSyncService.class);
        when(service.synchronizeDailyContent()).thenReturn(3);
        NetStartStartupSyncRunner runner = new NetStartStartupSyncRunner(properties(true), service);

        // 受控窗口只允许启动后调用一次，后续定时任务由独立开关关闭。
        runner.synchronizeOnce();

        verify(service).synchronizeDailyContent();
    }

    @Test
    void givenDisabledProvider_whenStartupRunnerExecutes_thenItDoesNotAccessProviderOrStorage() {
        ContentSyncService service = mock(ContentSyncService.class);
        NetStartStartupSyncRunner runner = new NetStartStartupSyncRunner(properties(false), service);

        // 两个开关必须同时打开；只开一次性同步开关不能意外触发外网和数据库写入。
        runner.synchronizeOnce();

        verifyNoInteractions(service);
    }

    private NetStartProperties properties(boolean enabled) {
        return new NetStartProperties(enabled, true, "https://apis.netstart.cn/maoyan", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(200));
    }
}
