package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcContentSummaryQueryAdapterTest {

    @Test
    void givenContentStorageUnreadable_whenBatchQuery_thenItReturns303004InsteadOfMissingIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        JdbcContentSummaryQueryAdapter adapter = new JdbcContentSummaryQueryAdapter(jdbcTemplate, Clock.systemUTC());

        assertThatThrownBy(() -> adapter.findCinemaSummaries(Set.of(8_100_001L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }
}
