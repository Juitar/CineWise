package com.miaoyu.ticket.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort.UserAdminSummary;
import com.miaoyu.ticket.auth.infrastructure.persistence.AuthUserMapper.AdminUserSummaryRow;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class MybatisUserAdminQueryAdapterTest {

    @Mock
    private AuthUserMapper mapper;

    @Mock
    private CurrentUserAccessor currentUserAccessor;

    private MybatisUserAdminQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MybatisUserAdminQueryAdapter(mapper, currentUserAccessor);
        when(currentUserAccessor.requireCurrentUser()).thenReturn(new CurrentUser(9001L, RoleCode.ADMIN, 0L));
    }

    @Test
    void shouldMatchPositiveNumericKeywordExactlyAndRejectInvalidRangesAsEmpty() {
        when(mapper.findExistingUserId(123L)).thenReturn(123L);

        assertThat(adapter.findUserIdsByKeyword(" 123 ")).containsExactly(123L);
        assertThat(adapter.findUserIdsByKeyword("0")).isEmpty();
        assertThat(adapter.findUserIdsByKeyword("99999999999999999999")).isEmpty();

        verify(mapper).findExistingUserId(123L);
    }

    @Test
    void shouldNormalizeAndEscapeEmailKeywordAndRejectTheOneHundredFirstMatch() {
        when(mapper.findUserIdsByEmailKeyword("a!%!_\\b", 101)).thenReturn(List.of(1L, 2L));

        assertThat(adapter.findUserIdsByKeyword(" A%_\\B ")).containsExactlyInAnyOrder(1L, 2L);

        List<Long> tooManyIds = LongStream.rangeClosed(1L, 101L).boxed().toList();
        when(mapper.findUserIdsByEmailKeyword("member", 101)).thenReturn(tooManyIds);
        assertErrorCode(
                () -> adapter.findUserIdsByKeyword("member"),
                AuthErrorCode.USER_QUERY_TOO_BROAD);
    }

    @Test
    void shouldReturnMaskedBatchSummariesAndKeepMissingUsersAbsent() {
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        when(mapper.findAdminUserSummaries(userIds)).thenReturn(List.of(
                new AdminUserSummaryRow(1L, "alice@example.com"),
                new AdminUserSummaryRow(3L, "carol@example.com")));

        Map<Long, UserAdminSummary> result = adapter.findByUserIds(userIds);

        assertThat(result)
                .containsEntry(1L, new UserAdminSummary(1L, "a***@example.com"))
                .containsEntry(3L, new UserAdminSummary(3L, "c***@example.com"))
                .doesNotContainKey(2L);
        assertThat(adapter.findByUserIds(Set.of())).isEmpty();
    }

    @Test
    void shouldRejectInvalidBatchInputsWithoutQueryingDirectory() {
        assertErrorCode(() -> adapter.findByUserIds(null), AuthErrorCode.INVALID_PARAMETER);

        Set<Long> withNull = new HashSet<>();
        withNull.add(null);
        assertErrorCode(() -> adapter.findByUserIds(withNull), AuthErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> adapter.findByUserIds(Set.of(0L)), AuthErrorCode.INVALID_PARAMETER);

        Set<Long> tooMany = new HashSet<>(LongStream.rangeClosed(1L, 101L).boxed().toList());
        assertErrorCode(() -> adapter.findByUserIds(tooMany), AuthErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldMapDirectoryFailuresWithoutReturningPartialResults() {
        when(mapper.findUserIdsByEmailKeyword("member", 101))
                .thenThrow(new DataAccessResourceFailureException("directory unavailable"));
        assertErrorCode(
                () -> adapter.findUserIdsByKeyword("member"),
                AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);

        Set<Long> userIds = Set.of(1L);
        when(mapper.findAdminUserSummaries(userIds))
                .thenThrow(new DataAccessResourceFailureException("directory unavailable"));
        assertErrorCode(
                () -> adapter.findByUserIds(userIds),
                AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);
    }

    @Test
    void shouldRequireAdminBeforeReadingDirectory() {
        when(currentUserAccessor.requireCurrentUser()).thenReturn(new CurrentUser(1001L, RoleCode.USER, 0L));

        assertErrorCode(() -> adapter.findUserIdsByKeyword("member"), AuthErrorCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectInvalidEmailKeywordLengths() {
        assertErrorCode(() -> adapter.findUserIdsByKeyword(null), AuthErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> adapter.findUserIdsByKeyword(" "), AuthErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> adapter.findUserIdsByKeyword("a"), AuthErrorCode.INVALID_PARAMETER);
        assertErrorCode(
                () -> adapter.findUserIdsByKeyword("a".repeat(101)),
                AuthErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(mapper);
    }

    private void assertErrorCode(ThrowingRunnable action, AuthErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
