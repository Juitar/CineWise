package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.auth.infrastructure.persistence.AuthUserMapper.AdminUserSummaryRow;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/**
 * C 提供给管理订单的正式用户目录适配器。
 *
 * <p>入口始终复核 ADMIN，查询只返回有限用户 ID 和脱敏邮箱，不把认证持久化对象暴露给 A。
 * 邮箱包含查询最多读取 101 个 ID，第 101 个只用于判断条件过宽，不能静默截断为前 100 个。</p>
 */
@Repository
public class MybatisUserAdminQueryAdapter implements UserAdminQueryPort {

    private static final int MAX_EMAIL_KEYWORD_LENGTH = 100;
    private static final int MIN_EMAIL_KEYWORD_LENGTH = 2;
    private static final int MAX_USER_IDS = 100;
    private static final int QUERY_LIMIT = MAX_USER_IDS + 1;
    private static final int MAX_NUMERIC_KEYWORD_LENGTH = 19;
    private static final Pattern ASCII_DIGITS = Pattern.compile("[0-9]+");

    private final AuthUserMapper mapper;
    private final CurrentUserAccessor currentUserAccessor;

    public MybatisUserAdminQueryAdapter(AuthUserMapper mapper, CurrentUserAccessor currentUserAccessor) {
        this.mapper = mapper;
        this.currentUserAccessor = currentUserAccessor;
    }

    @Override
    public Set<Long> findUserIdsByKeyword(String userKeyword) {
        requireAdmin();
        String normalizedKeyword = normalizeKeyword(userKeyword);
        if (ASCII_DIGITS.matcher(normalizedKeyword).matches()) {
            return findByNumericKeyword(normalizedKeyword);
        }
        if (normalizedKeyword.length() < MIN_EMAIL_KEYWORD_LENGTH
                || normalizedKeyword.length() > MAX_EMAIL_KEYWORD_LENGTH) {
            throw invalidParameter();
        }
        try {
            List<Long> matchedIds = mapper.findUserIdsByEmailKeyword(escapeLike(normalizedKeyword), QUERY_LIMIT);
            if (matchedIds.size() > MAX_USER_IDS) {
                throw new BusinessException(AuthErrorCode.USER_QUERY_TOO_BROAD);
            }
            return Set.copyOf(new LinkedHashSet<>(matchedIds));
        } catch (DataAccessException exception) {
            throw directoryUnavailable();
        }
    }

    @Override
    public Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds) {
        requireAdmin();
        Set<Long> validatedIds = validateUserIds(userIds);
        if (validatedIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, UserAdminSummary> summaries = new LinkedHashMap<>();
            for (AdminUserSummaryRow row : mapper.findAdminUserSummaries(validatedIds)) {
                summaries.put(row.id(), new UserAdminSummary(row.id(), EmailAddress.mask(row.email())));
            }
            return Map.copyOf(summaries);
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw directoryUnavailable();
        }
    }

    private Set<Long> findByNumericKeyword(String normalizedKeyword) {
        if (normalizedKeyword.length() > MAX_NUMERIC_KEYWORD_LENGTH) {
            return Set.of();
        }
        long userId;
        try {
            userId = Long.parseLong(normalizedKeyword);
        } catch (NumberFormatException exception) {
            return Set.of();
        }
        if (userId <= 0) {
            return Set.of();
        }
        try {
            Long existingUserId = mapper.findExistingUserId(userId);
            return existingUserId == null ? Set.of() : Set.of(existingUserId);
        } catch (DataAccessException exception) {
            throw directoryUnavailable();
        }
    }

    private String normalizeKeyword(String userKeyword) {
        if (userKeyword == null) {
            throw invalidParameter();
        }
        String normalized = userKeyword.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw invalidParameter();
        }
        return normalized;
    }

    private Set<Long> validateUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.size() > MAX_USER_IDS) {
            throw invalidParameter();
        }
        LinkedHashSet<Long> validated = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                throw invalidParameter();
            }
            validated.add(userId);
        }
        return Set.copyOf(validated);
    }

    /** 感叹号是显式 LIKE 转义字符；反斜杠本身保持普通字符。 */
    private String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private void requireAdmin() {
        CurrentUser currentUser = currentUserAccessor.requireCurrentUser();
        if (currentUser.role() != RoleCode.ADMIN) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN);
        }
    }

    private BusinessException invalidParameter() {
        return new BusinessException(AuthErrorCode.INVALID_PARAMETER);
    }

    private BusinessException directoryUnavailable() {
        return new BusinessException(AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);
    }
}
