package com.miaoyu.ticket.content.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.CityResolutionService;
import com.miaoyu.ticket.content.application.ContentSyncService;
import com.miaoyu.ticket.content.infrastructure.provider.NetStartProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

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

    @Test
    void givenEnabledProvider_whenStartupSyncRuns_thenItPassesAdministrativeCodeToCinemaSync() {
        ContentSyncService service = mock(ContentSyncService.class);
        when(service.synchronizeDailyContent()).thenReturn(3);
        NetStartProperties properties = new NetStartProperties(true, true, "https://example.test", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(200), List.of("430100"));
        NetStartStartupSyncRunner runner = new NetStartStartupSyncRunner(properties, service,
                new CityResolutionService(new ObjectMapper(), new DefaultResourceLoader()));

        runner.synchronizeOnce();

        verify(service).synchronizeCityCinemasWithResult(org.mockito.ArgumentMatchers.eq("长沙"),
                org.mockito.ArgumentMatchers.eq("430100"), org.mockito.ArgumentMatchers.any());
    }

    private NetStartProperties properties(boolean enabled) {
        return new NetStartProperties(enabled, true, "https://apis.netstart.cn/maoyan", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(200));
    }
}
