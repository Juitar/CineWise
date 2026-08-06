package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.ProfileTagPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/** 将设置和标签转换为最小摘要；关闭时不读取或暴露任何标签。 */
@Component
public class ProfileSummaryAssembler {
  private final Clock clock;

  public ProfileSummaryAssembler(Clock clock) {
    this.clock = clock;
  }

  public ProfileSummary assemble(
      ProfilePreferenceRepository.Snapshot preference, List<ProfileTagRepository.Snapshot> tags) {
    Instant now = clock.instant();
    if (!preference.personalizationEnabled()) {
      return new ProfileSummary(false, preference.version(), now, List.of());
    }
    return new ProfileSummary(
        true,
        preference.version(),
        now,
        tags.stream()
            .filter(
                tag ->
                    ProfileTagPolicy.isAvailableForSummary(
                        tag.status(),
                        tag.expiresAt() == null ? null : tag.expiresAt().toInstant(ZoneOffset.UTC),
                        now))
            .map(
                tag ->
                    new ProfileSummary.Tag(
                        tag.type(),
                        tag.value(),
                        tag.polarity(),
                        tag.weight(),
                        tag.confidence(),
                        tag.source(),
                        tag.updatedAt().toInstant(ZoneOffset.UTC)))
            .toList());
  }
}
