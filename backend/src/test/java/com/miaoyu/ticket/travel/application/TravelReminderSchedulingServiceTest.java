package com.miaoyu.ticket.travel.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 验证到期建议与观影结束关闭均通过 D 的持久化端口，不依赖单机线程锁。 */
class TravelReminderSchedulingServiceTest {
    @Test
    void shouldGenerateDueAdviceAndCompleteOnlyElapsedTasks() {
        TravelTaskRepository repository = mock(TravelTaskRepository.class);
        TravelAdviceService adviceService = mock(TravelAdviceService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.ofHours(8));
        TravelTaskRepository.TravelTaskSnapshot due = new TravelTaskRepository.TravelTaskSnapshot(
                91L, "90001", 1L, 2L, 3L, "西湖区", now.plusHours(3), now.minusMinutes(1), 1L, 0L,
                TravelTaskStatus.PENDING, null, now);
        when(repository.listDueForAdvice(now, 100)).thenReturn(List.of(due));
        when(repository.listElapsedTaskIds(now.minusHours(2L), 100)).thenReturn(List.of(92L));

        new TravelReminderSchedulingService(repository, adviceService, clock).runDueTasks();

        // 重复调度是否重复写快照由 TravelAdviceService 的版本条件更新裁决。
        verify(adviceService).generate(91L);
        // 关闭检查必须先于建议生成，避免同一轮调度先给已经过期的任务生成快照。
        InOrder callOrder = inOrder(repository);
        callOrder.verify(repository).listElapsedTaskIds(now.minusHours(2L), 100);
        callOrder.verify(repository).listDueForAdvice(now, 100);
        verify(repository).completeIfElapsed(92L, now.minusHours(2L), now);
    }
}
