package com.miaoyu.ticket.profile.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.profile.application.ProfileManagementService;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 画像页面的本人接口。
 * Controller 不接收 userId，也不读取 Mapper；身份、同意、并发和幂等由应用服务统一控制。
 */
@RestController
@RequestMapping("/api/v1/profile/me")
public class ProfileController {
  private final ProfileManagementService profileManagementService;

  public ProfileController(ProfileManagementService profileManagementService) {
    this.profileManagementService = profileManagementService;
  }

  @GetMapping("/tags")
  public Result<ProfilePageResponse> listTags(
      @RequestParam(defaultValue = "1") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @PositiveOrZero int size) {
    return Result.success(toPage(profileManagementService.listMyTags(page, size)));
  }

  @PostMapping("/tags")
  @ResponseStatus(HttpStatus.CREATED)
  public Result<TagResponse> createTag(
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateTagRequest request) {
    return Result.success(toTag(profileManagementService.createMyManualTag(
        new ProfileManagementService.CreateTagCommand(parseVersion(ifMatch), idempotencyKey, request.type(),
            request.value(), request.polarity(), request.weight()))));
  }

  @PutMapping("/tags/{tagId}")
  public Result<TagResponse> updateTag(
      @PathVariable String tagId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody UpdateTagRequest request) {
    return Result.success(toTag(profileManagementService.updateMyTag(tagId,
        new ProfileManagementService.UpdateTagCommand(parseVersion(ifMatch), idempotencyKey,
            request.polarity(), request.weight(), request.status()))));
  }

  @DeleteMapping("/tags/{tagId}")
  public Result<Void> deleteTag(
      @PathVariable String tagId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    profileManagementService.deleteMyTag(tagId,
        new ProfileManagementService.DeleteTagCommand(parseVersion(ifMatch), idempotencyKey));
    return Result.success();
  }

  @PutMapping("/personalization")
  public Result<PreferenceResponse> updatePersonalization(
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody UpdatePersonalizationRequest request) {
    return Result.success(toPreference(profileManagementService.updateMyPersonalization(
        new ProfileManagementService.UpdatePersonalizationCommand(parseVersion(ifMatch), idempotencyKey,
            request.enabled()))));
  }

  private long parseVersion(String ifMatch) {
    try {
      long version = Long.parseLong(ifMatch.replace("\"", ""));
      if (version < 0) {
        throw new NumberFormatException();
      }
      return version;
    } catch (NumberFormatException exception) {
      throw new BusinessException(com.miaoyu.ticket.profile.application.ProfileErrorCode.INVALID_TAG,
          "If-Match 必须是非负画像版本");
    }
  }

  private ProfilePageResponse toPage(ProfileManagementService.ProfilePage page) {
    return new ProfilePageResponse(
        toPreference(page.preference()), page.tags().stream().map(this::toTag).toList(), page.total());
  }

  private PreferenceResponse toPreference(ProfileManagementService.PreferenceView preference) {
    return new PreferenceResponse(preference.enabled(), preference.version(), toOffset(preference.updatedAt()));
  }

  private TagResponse toTag(ProfileManagementService.TagView tag) {
    return new TagResponse(String.valueOf(tag.id()), tag.type().name(), tag.value(), tag.polarity().name(),
        tag.weight(), tag.confidence(), tag.source().name(), tag.status().name(), toOffset(tag.expiresAt()),
        tag.version(), toOffset(tag.updatedAt()));
  }

  private OffsetDateTime toOffset(LocalDateTime time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }

  public record CreateTagRequest(
      @NotNull ProfileTagType type,
      @NotBlank String value,
      @NotNull ProfileTagPolarity polarity,
      @NotNull @DecimalMin("0.100") @DecimalMax("1.000") BigDecimal weight) { }

  public record UpdateTagRequest(
      ProfileTagPolarity polarity,
      @DecimalMin("0.100") @DecimalMax("1.000") BigDecimal weight,
      ProfileTagStatus status) { }

  public record UpdatePersonalizationRequest(boolean enabled) { }

  public record ProfilePageResponse(PreferenceResponse preference, List<TagResponse> tags, long total) { }

  public record PreferenceResponse(boolean enabled, long version, OffsetDateTime updatedAt) { }

  public record TagResponse(
      String id, String type, String value, String polarity, BigDecimal weight, BigDecimal confidence,
      String source, String status, OffsetDateTime expiresAt, long version, OffsetDateTime updatedAt) { }
}
