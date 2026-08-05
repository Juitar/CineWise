package com.miaoyu.ticket.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 默认设置与本人标签查询骨架；实际创建前由后续写服务校验 C 的画像保存同意。 */
public class ProfileQueryService {
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileSummaryAssembler summaryAssembler;
  private final ProfileSummaryCache summaryCache;
  private final Clock clock;

  public ProfileQueryService(
      ProfilePreferenceRepository preferenceRepository,
      ProfileTagRepository tagRepository,
      ProfileSummaryAssembler summaryAssembler,
      ProfileSummaryCache summaryCache,
      Clock clock) {
    this.preferenceRepository = preferenceRepository;
    this.tagRepository = tagRepository;
    this.summaryAssembler = summaryAssembler;
    this.summaryCache = summaryCache;
    this.clock = clock;
  }

  /** 读取既有设置；首次创建由写服务在同意校验后的事务中调用 ensureDefault。 */
  public ProfilePreferenceRepository.Snapshot requireExistingPreference(long userId) {
    return preferenceRepository
        .findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("画像默认设置尚未创建"));
  }

  public void ensureDefault(long userId) {
    preferenceRepository.insertDefault(
        userId, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
  }

  public List<ProfileTagRepository.Snapshot> findTags(long userId, int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 100) {
      throw new IllegalArgumentException("分页参数不合法");
    }
    return tagRepository.findPageByUserId(userId, offset, limit);
  }

  public ProfileSummary assembleSummary(long userId) {
    ProfilePreferenceRepository.Snapshot preference = requireExistingPreference(userId);
    return summaryCache
        .find(userId, preference.version())
        .orElseGet(
            () -> {
              ProfileSummary summary = summaryAssembler.assemble(preference, findTags(userId, 0, 100));
              summaryCache.put(userId, preference.version(), summary);
              return summary;
            });
  }
}
