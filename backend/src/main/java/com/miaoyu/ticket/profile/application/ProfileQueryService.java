package com.miaoyu.ticket.profile.application;

import java.util.List;
import org.springframework.stereotype.Service;

/** 本人标签与摘要查询服务；默认设置创建必须由接入同意校验后的写服务负责。 */
@Service
public class ProfileQueryService {
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileSummaryAssembler summaryAssembler;
  private final ProfileSummaryCache summaryCache;
  public ProfileQueryService(
      ProfilePreferenceRepository preferenceRepository,
      ProfileTagRepository tagRepository,
      ProfileSummaryAssembler summaryAssembler,
      ProfileSummaryCache summaryCache) {
    this.preferenceRepository = preferenceRepository;
    this.tagRepository = tagRepository;
    this.summaryAssembler = summaryAssembler;
    this.summaryCache = summaryCache;
  }

  /** 只读取既有设置；未接入 C 的同意查询前，不提供默认设置创建入口。 */
  public ProfilePreferenceRepository.Snapshot requireExistingPreference(long userId) {
    return preferenceRepository
        .findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("画像默认设置尚未创建"));
  }

  public List<ProfileTagRepository.Snapshot> findTags(long userId, int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 100) {
      throw new IllegalArgumentException("分页参数不合法");
    }
    return tagRepository.findPageByUserId(userId, offset, limit);
  }

  public ProfileSummary assembleSummary(long userId) {
    ProfilePreferenceRepository.Snapshot preference = preferenceRepository.findByUserId(userId).orElse(null);
    if (preference == null) {
      // 没有画像设置时不能创建默认记录；只返回关闭摘要，避免读取行为或把登录状态当成同意。
      return new ProfileSummary(false, 0, java.time.Instant.now(java.time.Clock.systemUTC()), List.of());
    }
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
