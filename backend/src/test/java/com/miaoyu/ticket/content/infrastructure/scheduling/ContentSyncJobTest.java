package com.miaoyu.ticket.content.infrastructure.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.CityResolutionService;
import com.miaoyu.ticket.content.application.ContentSyncService;
import com.miaoyu.ticket.content.application.LiveContentSyncPort;
import com.miaoyu.ticket.content.infrastructure.provider.NetStartProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

class ContentSyncJobTest {
    @Test
    void givenControlledCity_whenDailyJobRuns_thenItSynchronizesMoviesAndThatCityCinemas() {
        ContentSyncService service = Mockito.mock(ContentSyncService.class);
        when(service.synchronizeCityCinemasWithResult(eq("长沙"), eq("70"), any()))
                .thenReturn(new ContentSyncService.CurrentHotMovieSyncResult(1, 1, 0,
                        LiveContentSyncPort.Outcome.SUCCESS, null));
        when(service.synchronizeCityCinemasWithResult(eq("杭州"), eq("50"), any()))
                .thenReturn(new ContentSyncService.CurrentHotMovieSyncResult(1, 1, 0,
                        LiveContentSyncPort.Outcome.SUCCESS, null));
        NetStartProperties properties = new NetStartProperties(true, false, "https://example.test", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(200), List.of("长沙", "杭州"));
        ContentSyncJob job = new ContentSyncJob(service,
                new CityResolutionService(new ObjectMapper(), new DefaultResourceLoader()), properties);

        job.synchronizeDailyContent();

        verify(service).synchronizeCurrentHotMovies();
        verify(service).synchronizeCityCinemasWithResult(eq("长沙"), eq("70"), any());
        verify(service).synchronizeCityCinemasWithResult(eq("杭州"), eq("50"), any());
    }
}
