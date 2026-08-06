package com.miaoyu.ticket.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 画像撤回后的分期清理任务。
 * 每次限量删除，避免长事务锁住整张画像表；审计只记录数量，不记录标签值、原始 payload 或会话内容。
 */
@Service
public class ProfileDataCleanupJob {
  private static final int BATCH_SIZE = 100;
  private final ProfileTagRepository tagRepository;
  private final ProfileBehaviorEventRepository eventRepository;
  private final ProfileWriteRequestRepository writeRequestRepository;
  private final Clock clock;
  private final ProfileAuditLogger auditLogger;
  private final Counter successCounter;
  private final Counter failureCounter;

  public ProfileDataCleanupJob(
      ProfileTagRepository tagRepository,
      ProfileBehaviorEventRepository eventRepository,
      ProfileWriteRequestRepository writeRequestRepository,
      Clock clock,
      ProfileAuditLogger auditLogger,
      MeterRegistry meterRegistry) {
    this.tagRepository = tagRepository;
    this.eventRepository = eventRepository;
    this.writeRequestRepository = writeRequestRepository;
    this.clock = clock;
    this.auditLogger = auditLogger;
    this.successCounter = meterRegistry.counter("profile.cleanup.completed");
    this.failureCounter = meterRegistry.counter("profile.cleanup.failed");
  }

  @Transactional
  @Scheduled(cron = "${cinewise.profile.cleanup-cron:0 45 2 * * *}")
  public int executeOnce() {
    try {
      LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
      int deletedTags = tagRepository.cleanupDeletedBefore(now.minusDays(30), BATCH_SIZE);
      int deletedEvents = eventRepository.cleanupBefore(now.minusDays(90), BATCH_SIZE);
      int deletedRequests = writeRequestRepository.cleanupExpired(now, BATCH_SIZE);
      auditLogger.record(java.util.Map.of(
          "operation", "PROFILE_CLEANUP",
          "result", "tags=" + deletedTags + ",events=" + deletedEvents + ",requests=" + deletedRequests));
      successCounter.increment();
      return deletedTags + deletedEvents + deletedRequests;
    } catch (RuntimeException exception) {
      // 清理失败必须留下可告警的最小结果；不记录异常文本，避免数据库错误里携带用户输入或 SQL 参数。
      auditLogger.record(java.util.Map.of(
          "operation", "PROFILE_CLEANUP",
          "result", "FAILED",
          "errorCode", "PROFILE_CLEANUP_FAILED"));
      failureCounter.increment();
      throw exception;
    }
  }
}
