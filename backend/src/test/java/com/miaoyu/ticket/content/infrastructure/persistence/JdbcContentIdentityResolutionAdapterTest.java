package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentIdentityResolutionPort.ResolutionStatus;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcContentIdentityResolutionAdapterTest {

    @Test
    void givenActiveInvalidAndMissingMappings_whenResolve_thenItReturnsStableStatuses() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:identity-resolution;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE content_identity_mapping (
                    id BIGINT PRIMARY KEY, provider VARCHAR(64), resource_type VARCHAR(16),
                    external_id VARCHAR(128), internal_content_id BIGINT, status VARCHAR(16))
                """);
        jdbc.update("INSERT INTO content_identity_mapping VALUES (1, 'NETSTART_MAOYAN', 'MOVIE', 'm1', 101, 'ACTIVE')");
        jdbc.update("INSERT INTO content_identity_mapping VALUES (2, 'NETSTART_MAOYAN', 'MOVIE', 'm2', 102, "
                + "'INVALID')");
        jdbc.update("INSERT INTO content_identity_mapping VALUES (3, 'NETSTART_MAOYAN', 'MOVIE', 'm4', 104, 'ACTIVE')");
        jdbc.update("INSERT INTO content_identity_mapping VALUES (4, 'NETSTART_MAOYAN', 'MOVIE', 'm4', 105, 'ACTIVE')");

        var result = new JdbcContentIdentityResolutionAdapter(jdbc)
                .resolve("NETSTART_MAOYAN", ContentResourceType.MOVIE, List.of("m1", "m2", "m3", "m4"));

        assertThat(result.results()).extracting(item -> item.status())
                .containsExactly(ResolutionStatus.RESOLVED, ResolutionStatus.INVALIDATED,
                        ResolutionStatus.NOT_FOUND, ResolutionStatus.AMBIGUOUS);
        assertThat(result.results().getFirst().internalContentId()).isEqualTo(101L);
        assertThat(result.results().get(1).errorCode()).isEqualTo(303007);
    }
}
