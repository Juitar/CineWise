package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcContentExternalIdentityLookupAdapterTest {

    @Test
    void givenActiveCinemaMapping_whenLookup_thenItReturnsOnlyExternalIdAndInternalProviderCityId() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:showtime-identity;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE cinema (id BIGINT PRIMARY KEY, provider_city_id VARCHAR(32))");
        jdbc.execute("""
                CREATE TABLE content_identity_mapping (
                    id BIGINT PRIMARY KEY, provider VARCHAR(64), resource_type VARCHAR(16),
                    external_id VARCHAR(128), internal_content_id BIGINT, status VARCHAR(16))
                """);
        jdbc.update("INSERT INTO cinema VALUES (21, '70')");
        jdbc.update("INSERT INTO content_identity_mapping VALUES (1, 'NETSTART_MAOYAN', 'CINEMA', 'c1', 21, 'ACTIVE')");

        assertThat(new JdbcContentExternalIdentityLookupAdapter(jdbc).findActiveExternalIds(
                "NETSTART_MAOYAN", ContentResourceType.CINEMA, List.of(21L)))
                .containsExactly(new com.miaoyu.ticket.content.application.ContentExternalIdentityLookupPort
                        .ExternalIdentity(21L, "c1", "70"));
    }
}
