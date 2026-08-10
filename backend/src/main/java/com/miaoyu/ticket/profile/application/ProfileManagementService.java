package com.miaoyu.ticket.profile.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagPolicy;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.profile.domain.ProfileTagValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人画像写入的唯一入口。
 * 所有 HTTP 参数只描述动作，用户身份始终从认证上下文取得；同意、版本、幂等和缓存失效必须在同一事务处理。
 */
@Service
public class ProfileManagementService {
  private static final String OPERATION_CREATE_TAG = "CREATE_TAG";
  private static final String OPERATION_UPDATE_TAG = "UPDATE_TAG";
  private static final String OPERATION_DELETE_TAG = "DELETE_TAG";
  private static final String OPERATION_UPDATE_PERSONALIZATION = "UPDATE_PERSONALIZATION";
  private static final BigDecimal MANUAL_CONFIDENCE = new BigDecimal("1.000");

  private final CurrentUserAccessor currentUserAccessor;
  private final ProfileDataConsentQuery consentQuery;
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileWriteRequestRepository writeRequestRepository;
  private final ProfileSummaryCache summaryCache;
  private final BusinessIdGenerator idGenerator;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public ProfileManagementService(
      CurrentUserAccessor currentUserAccessor,
      ProfileDataConsentQuery consentQuery,
      ProfilePreferenceRepository preferenceRepository,
      ProfileTagRepository tagRepository,
      ProfileWriteRequestRepository writeRequestRepository,
      ProfileSummaryCache summaryCache,
      BusinessIdGenerator idGenerator,
      Clock clock,
      ObjectMapper objectMapper) {
    this.currentUserAccessor = currentUserAccessor;
    this.consentQuery = consentQuery;
    this.preferenceRepository = preferenceRepository;
    this.tagRepository = tagRepository;
    this.writeRequestRepository = writeRequestRepository;
    this.summaryCache = summaryCache;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public TagView createMyManualTag(CreateTagCommand command) {
    // 手工标签必须在同意有效、版本匹配和幂等键校验后写入，写入成功才清理画像缓存。
    long userId = requireConsentAndUser();
    validateKey(command.idempotencyKey());
    String hash = ProfileRequestHasher.sha256(command.canonicalValue());
    WriteReplay replay = findReplay(userId, OPERATION_CREATE_TAG, command.idempotencyKey(), hash);
    if (replay != null) {
      return deserialize(replay.responseJson(), TagView.class);
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    requireVersion(preference, command.expectedVersion());
    validateManual(command.type(), command.value(), command.polarity(), command.weight());
    LocalDateTime now = now();
    TagView view = new TagView(
        idGenerator.nextId(), command.type(), normalizeValue(command.value()), command.polarity(),
        command.weight(), MANUAL_CONFIDENCE, ProfileTagSource.MANUAL, ProfileTagStatus.ACTIVE, null, 0, now);
    try {
      tagRepository.insert(view.toNewTag(userId, now));
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(ProfileErrorCode.DUPLICATE_TAG);
    }
    advancePreferenceOrConflict(userId, preference.version(), now);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, OPERATION_CREATE_TAG, command.idempotencyKey(), hash, 201, serialize(view), now);
    return view;
  }

  /**
   * 仅供 Agent 在用户明确确认后写入长期对话偏好。
   * 该入口自行读取版本，调用方不能伪造用户、画像版本或标签来源。
   */
  @Transactional
  public TagView saveMyConversationPreference(ConversationPreferenceCommand command) {
    // Agent 只有在用户确认后才能调用此入口；来源固定为 CONVERSATION，不能由请求指定。
    long userId = requireConsentAndUser();
    validateKey(command.idempotencyKey());
    String hash = ProfileRequestHasher.sha256(command.canonicalValue());
    WriteReplay replay = findReplay(userId, "SAVE_CONVERSATION_PREFERENCE", command.idempotencyKey(), hash);
    if (replay != null) {
      return deserialize(replay.responseJson(), TagView.class);
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    validateConversation(command.type(), command.value(), command.polarity());
    String value = normalizeValue(command.value());
    LocalDateTime now = now();
    TagView view = new TagView(
        idGenerator.nextId(), command.type(), value, command.polarity(), BigDecimal.ONE, BigDecimal.ONE,
        ProfileTagSource.CONVERSATION, ProfileTagStatus.ACTIVE, null, 0, now);
    try {
      tagRepository.insert(view.toNewTag(userId, now));
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(ProfileErrorCode.DUPLICATE_TAG);
    }
    advancePreferenceOrConflict(userId, preference.version(), now);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, "SAVE_CONVERSATION_PREFERENCE", command.idempotencyKey(), hash, 201, serialize(view), now);
    return view;
  }

  @Transactional
  public TagView updateMyTag(String tagId, UpdateTagCommand command) {
    // 只允许修改本人创建的手工标签；画像版本和标签版本同时参与并发控制。
    long userId = requireConsentAndUser();
    long parsedTagId = parseId(tagId);
    validateKey(command.idempotencyKey());
    String hash = ProfileRequestHasher.sha256(command.canonicalValue(parsedTagId));
    WriteReplay replay = findReplay(userId, OPERATION_UPDATE_TAG, command.idempotencyKey(), hash);
    if (replay != null) {
      return deserialize(replay.responseJson(), TagView.class);
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    requireVersion(preference, command.expectedVersion());
    ProfileTagRepository.Snapshot existing = findMyTag(parsedTagId, userId);
    if (existing.source() != ProfileTagSource.MANUAL
        || (existing.status() != ProfileTagStatus.ACTIVE && existing.status() != ProfileTagStatus.DISABLED)) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
    if (command.status() != null) {
      return updateMyTagStatus(userId, parsedTagId, preference, existing, command, hash, now());
    }
    if (existing.status() != ProfileTagStatus.ACTIVE) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
    validateManual(existing.type(), existing.value(), command.polarity(), command.weight());
    LocalDateTime now = now();
    if (!tagRepository.update(parsedTagId, userId, existing.version(), command.polarity(), command.weight(),
        MANUAL_CONFIDENCE, null, now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
    advancePreferenceOrConflict(userId, preference.version(), now);
    TagView view = TagView.from(
        existing,
        command.polarity(),
        command.weight(),
        MANUAL_CONFIDENCE,
        now,
        existing.version() + 1);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, OPERATION_UPDATE_TAG, command.idempotencyKey(), hash, 200, serialize(view), now);
    return view;
  }

  private TagView updateMyTagStatus(
      long userId,
      long tagId,
      ProfilePreferenceRepository.Snapshot preference,
      ProfileTagRepository.Snapshot existing,
      UpdateTagCommand command,
      String hash,
      LocalDateTime now) {
    // 启用/停用是独立动作，不允许同一个请求夹带极性或权重变化。
    if ((command.status() != ProfileTagStatus.ACTIVE && command.status() != ProfileTagStatus.DISABLED)
        || command.polarity() != null || command.weight() != null) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
    if (!tagRepository.updateStatus(tagId, userId, existing.version(), command.status(), now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
    advancePreferenceOrConflict(userId, preference.version(), now);
    TagView view = new TagView(
        existing.id(), existing.type(), existing.value(), existing.polarity(), existing.weight(),
        existing.confidence(), existing.source(), command.status(), existing.expiresAt(),
        existing.version() + 1, now);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, OPERATION_UPDATE_TAG, command.idempotencyKey(), hash, 200, serialize(view), now);
    return view;
  }

  @Transactional
  public void deleteMyTag(String tagId, DeleteTagCommand command) {
    // 删除采用软删除并推进画像版本，历史推荐仍可审计但不再读取该标签。
    long userId = requireConsentAndUser();
    long parsedTagId = parseId(tagId);
    validateKey(command.idempotencyKey());
    String hash = ProfileRequestHasher.sha256("DELETE|" + parsedTagId);
    WriteReplay replay = findReplay(userId, OPERATION_DELETE_TAG, command.idempotencyKey(), hash);
    if (replay != null) {
      return;
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    requireVersion(preference, command.expectedVersion());
    ProfileTagRepository.Snapshot existing = findMyTag(parsedTagId, userId);
    LocalDateTime now = now();
    if (!tagRepository.softDelete(parsedTagId, userId, existing.version(), now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
    advancePreferenceOrConflict(userId, preference.version(), now);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, OPERATION_DELETE_TAG, command.idempotencyKey(), hash, 200, "{}", now);
  }

  @Transactional
  public PreferenceView updateMyPersonalization(UpdatePersonalizationCommand command) {
    // 个性化开关与标签共用画像版本，关闭后推荐服务应读取最新开关状态。
    long userId = requireConsentAndUser();
    validateKey(command.idempotencyKey());
    String hash = ProfileRequestHasher.sha256("PERSONALIZATION|" + command.enabled());
    WriteReplay replay = findReplay(userId, OPERATION_UPDATE_PERSONALIZATION, command.idempotencyKey(), hash);
    if (replay != null) {
      return deserialize(replay.responseJson(), PreferenceView.class);
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    requireVersion(preference, command.expectedVersion());
    LocalDateTime now = now();
    if (!preferenceRepository.updatePersonalization(userId, preference.version(), command.enabled(), now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
    PreferenceView view = new PreferenceView(command.enabled(), preference.version() + 1, now);
    summaryCache.invalidateUser(userId);
    saveReplay(userId, OPERATION_UPDATE_PERSONALIZATION, command.idempotencyKey(), hash, 200, serialize(view), now);
    return view;
  }

  public ProfilePage listMyTags(int page, int size) {
    // 列表只返回当前用户的有效画像设置和标签，分页上限防止一次拉取过大。
    long userId = requireConsentAndUser();
    if (page < 1 || size < 1 || size > 100) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
    ProfilePreferenceRepository.Snapshot preference = requirePreference(userId);
    int offset = Math.multiplyExact(page - 1, size);
    List<TagView> tags = tagRepository.findPageByUserId(userId, offset, size).stream().map(TagView::from).toList();
    return new ProfilePage(
        new PreferenceView(preference.personalizationEnabled(), preference.version(), preference.updatedAt()),
        tags,
        tagRepository.countByUserId(userId));
  }

  private long requireConsentAndUser() {
    // 先取认证用户再检查同意记录；不接受客户端传入 userId 绕过隐私授权。
    long userId = currentUserAccessor.requireCurrentUserId();
    if (!consentQuery.findByUserId(userId).granted()) {
      throw new BusinessException(ProfileErrorCode.PROFILE_DATA_CONSENT_REQUIRED);
    }
    return userId;
  }

  private ProfilePreferenceRepository.Snapshot requirePreference(long userId) {
    // 首次访问惰性创建默认画像设置，保证后续版本更新始终有基线。
    return preferenceRepository.findByUserId(userId).orElseGet(() -> createDefaultPreference(userId));
  }

  private ProfilePreferenceRepository.Snapshot createDefaultPreference(long userId) {
    LocalDateTime now = now();
    try {
      preferenceRepository.insertDefault(userId, now);
    } catch (DuplicateKeyException ignored) {
      // 并发首次访问只有一个事务能插入；另一个事务必须读取已存在设置，而不是报错或创建第二行。
    }
    return preferenceRepository.findByUserId(userId).orElseThrow(() -> new IllegalStateException("默认画像设置未创建"));
  }

  private void advancePreferenceOrConflict(long userId, long expectedVersion, LocalDateTime now) {
    // 版本推进使用条件更新，失败说明另一个请求先修改过画像，不能覆盖其结果。
    if (!preferenceRepository.incrementVersionIfMatches(userId, expectedVersion, now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
  }

  private ProfileTagRepository.Snapshot findMyTag(long tagId, long userId) {
    return tagRepository
        .findByIdAndUserId(tagId, userId)
        .orElseThrow(() -> new BusinessException(ProfileErrorCode.TAG_NOT_FOUND));
  }

  private void requireVersion(ProfilePreferenceRepository.Snapshot preference, long expectedVersion) {
    if (expectedVersion != preference.version()) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
  }

  private WriteReplay findReplay(long userId, String operation, String key, String hash) {
    // 幂等记录按用户、操作和键查询，找到记录后还要校验参数哈希。
    return writeRequestRepository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, key)
        .map(row -> decideReplay(row, hash)).orElse(null);
  }

  private WriteReplay decideReplay(ProfileWriteRequestRepository.Snapshot row, String hash) {
    // 同一幂等键提交不同参数必须报错，禁止返回与本次请求不匹配的旧响应。
    if (ProfileWriteRequestDecision.decide(row.requestHash(), hash)
        == ProfileWriteRequestDecision.REJECT_PARAMETER_MISMATCH) {
      throw new BusinessException(ProfileErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
    }
    return new WriteReplay(row.responseJson());
  }

  private void saveReplay(
      long userId,
      String operation,
      String key,
      String hash,
      int status,
      String response,
      LocalDateTime now) {
    // 保存完整响应供客户端重试复用，保留期与画像写入审计周期一致。
    writeRequestRepository.insert(new ProfileWriteRequestRepository.NewRequest(
        idGenerator.nextId(), userId, operation, key, hash, status, response, now, now.plusDays(30)));
  }

  private String serialize(Object value) {
    // 幂等响应使用明确 DTO 序列化，不保存模型原文或请求中的额外字段。
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("画像幂等响应序列化失败", exception);
    }
  }

  private <T> T deserialize(String value, Class<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("画像幂等响应恢复失败", exception);
    }
  }

  private void validateManual(ProfileTagType type, String value, ProfileTagPolarity polarity, BigDecimal weight) {
    // 手工标签沿用 ProfileTagPolicy，确保来源、权重和字段长度规则只有一份。
    try {
      normalizeValue(value);
      if (type == null || polarity == null
          || !ProfileTagPolicy.isWeightValid(ProfileTagSource.MANUAL, weight, null)) {
        throw new IllegalArgumentException("手工标签参数不合法");
      }
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
  }

  private void validateConversation(ProfileTagType type, String value, ProfileTagPolarity polarity) {
    // 对话偏好固定置信度和来源，只验证允许的类型、文本和极性。
    try {
      normalizeValue(value);
      if (type == null || polarity == null
          || !ProfileTagPolicy.isWeightValid(ProfileTagSource.CONVERSATION, BigDecimal.ONE, null)) {
        throw new IllegalArgumentException("对话标签参数不合法");
      }
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
  }

  private String normalizeValue(String value) {
    // 统一去除首尾空白并执行标签值长度校验，避免同义文本产生重复标签。
    return new ProfileTagValue(value).value();
  }

  private void validateKey(String key) {
    if (key == null || key.isBlank() || key.length() > 128) {
      throw new BusinessException(ProfileErrorCode.INVALID_TAG);
    }
  }

  private long parseId(String id) {
    try {
      long parsed = Long.parseLong(id);
      if (parsed <= 0) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new BusinessException(ProfileErrorCode.TAG_NOT_FOUND);
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public record CreateTagCommand(
      long expectedVersion, String idempotencyKey, ProfileTagType type, String value,
      ProfileTagPolarity polarity, BigDecimal weight) {
    String canonicalValue() { return type + "|" + value + "|" + polarity + "|" + weight; }
  }

  public record ConversationPreferenceCommand(
      String idempotencyKey, ProfileTagType type, String value, ProfileTagPolarity polarity) {
    String canonicalValue() { return type + "|" + value + "|" + polarity; }
  }

  public record UpdateTagCommand(
      long expectedVersion,
      String idempotencyKey,
      ProfileTagPolarity polarity,
      BigDecimal weight,
      ProfileTagStatus status) {
    String canonicalValue(long tagId) {
      return tagId + "|" + polarity + "|" + weight + "|" + status;
    }
  }

  public record DeleteTagCommand(long expectedVersion, String idempotencyKey) { }

  public record UpdatePersonalizationCommand(long expectedVersion, String idempotencyKey, boolean enabled) { }

  public record ProfilePage(PreferenceView preference, List<TagView> tags, long total) { }

  public record PreferenceView(boolean enabled, long version, LocalDateTime updatedAt) {
  }

  public record TagView(long id, ProfileTagType type, String value, ProfileTagPolarity polarity, BigDecimal weight,
                        BigDecimal confidence, ProfileTagSource source, ProfileTagStatus status,
                        LocalDateTime expiresAt, long version, LocalDateTime updatedAt) {
    static TagView from(ProfileTagRepository.Snapshot tag) {
      return new TagView(tag.id(), tag.type(), tag.value(), tag.polarity(), tag.weight(), tag.confidence(),
          tag.source(), tag.status(), tag.expiresAt(), tag.version(), tag.updatedAt());
    }
    static TagView from(ProfileTagRepository.Snapshot tag, ProfileTagPolarity polarity, BigDecimal weight,
                        BigDecimal confidence, LocalDateTime updatedAt, long version) {
      return new TagView(tag.id(), tag.type(), tag.value(), polarity, weight, confidence, tag.source(),
          tag.status(), tag.expiresAt(), version, updatedAt);
    }
    ProfileTagRepository.NewTag toNewTag(long userId, LocalDateTime now) {
      return new ProfileTagRepository.NewTag(id, userId, type, value, polarity, weight, source, confidence,
          status, expiresAt, now);
    }
  }

  private record WriteReplay(String responseJson) { }
}
