package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class TravelOrderSummaryQueryServiceTest {

    private static final long USER_ID = 77L;
    private static final long ORDER_ID = 10001L;

    @Test
    void givenOwnedOrder_whenQuery_thenReturnStringIdsAndBusinessOffset() {
        OrderRepository repository = mock(OrderRepository.class);
        CurrentUserAccessor currentUser = mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        when(repository.findTravelOrderSummaryByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(snapshot()));

        TravelOrderSummaryQueryPort.TravelOrderSummary result =
                new TravelOrderSummaryQueryService(repository, currentUser).queryMyOrder("10001");

        assertThat(result.orderId()).isEqualTo("10001");
        assertThat(result.orderNo()).isEqualTo("CW-10001");
        assertThat(result.showId()).isEqualTo("20001");
        assertThat(result.movieId()).isEqualTo("30001");
        assertThat(result.cinemaId()).isEqualTo("40001");
        assertThat(result.showStartTime().toString()).isEqualTo("2026-08-10T14:30+08:00");
    }

    @Test
    void givenMalformedOrderIds_whenQuery_thenRejectBeforeRepository() {
        OrderRepository repository = mock(OrderRepository.class);
        CurrentUserAccessor currentUser = mock(CurrentUserAccessor.class);
        TravelOrderSummaryQueryService service = new TravelOrderSummaryQueryService(repository, currentUser);

        for (String orderId : Arrays.asList(null, "", " ", "0", "01", "-1", "9223372036854775808")) {
            assertThatThrownBy(() -> service.queryMyOrder(orderId))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        }
        verify(currentUser, never()).requireCurrentUserId();
        verify(repository, never()).findTravelOrderSummaryByIdAndUserId(ORDER_ID, USER_ID);
    }

    @Test
    void givenMissingOrOtherUsersOrder_whenQuery_thenHideOwnership() {
        OrderRepository repository = mock(OrderRepository.class);
        CurrentUserAccessor currentUser = mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        when(repository.findTravelOrderSummaryByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new TravelOrderSummaryQueryService(repository, currentUser)
                .queryMyOrder("10001"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void givenDatabaseUnavailable_whenQuery_thenReturnStableUnavailableError() {
        OrderRepository repository = mock(OrderRepository.class);
        CurrentUserAccessor currentUser = mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        when(repository.findTravelOrderSummaryByIdAndUserId(ORDER_ID, USER_ID))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> new TravelOrderSummaryQueryService(repository, currentUser)
                .queryMyOrder("10001"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_QUERY_UNAVAILABLE));
    }

    @Test
    void givenInvalidAuthoritativeSnapshot_whenQuery_thenDoNotPublishForgedIdentifiers() {
        OrderRepository repository = mock(OrderRepository.class);
        CurrentUserAccessor currentUser = mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        when(repository.findTravelOrderSummaryByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(new OrderRepository.TravelOrderSummarySnapshot(
                        ORDER_ID,
                        "CW-10001",
                        20001L,
                        30001L,
                        0L,
                        LocalDateTime.of(2026, 8, 10, 14, 30))));

        assertThatThrownBy(() -> new TravelOrderSummaryQueryService(repository, currentUser)
                .queryMyOrder("10001"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_QUERY_UNAVAILABLE));
    }

    private OrderRepository.TravelOrderSummarySnapshot snapshot() {
        return new OrderRepository.TravelOrderSummarySnapshot(
                ORDER_ID,
                "CW-10001",
                20001L,
                30001L,
                40001L,
                LocalDateTime.of(2026, 8, 10, 14, 30));
    }
}
