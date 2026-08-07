package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.miaoyu.ticket.content.application.ExternalShowtimeProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NetStartShowtimeProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void givenQualifiedProviderResponse_whenFetch_thenItKeepsOnlyCandidateFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://netstart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://netstart.test/cinema/shows?ci=70&cinemaId=c1"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"success":true,"data":{"movies":[{"id":101,"shows":[{"showDate":"2026-08-07",
                        "plist":[{"seqNo":"s1","tm":"11:30","vipPrice":"36","th":"1号厅","lang":"国语"}]}]}]}}"""));
        NetStartShowtimeProvider provider = provider(builder.build());

        ExternalShowtimeProvider.FetchResult result = provider.fetch(LocalDate.of(2026, 8, 7),
                List.of(new ExternalShowtimeProvider.ExternalCinema("c1", "70")));

        assertThat(result.available()).isTrue();
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.externalShowId()).isEqualTo("s1");
            assertThat(candidate.externalMovieId()).isEqualTo("101");
            assertThat(candidate.externalCinemaId()).isEqualTo("c1");
            assertThat(candidate.startTime().toString()).isEqualTo("2026-08-07T11:30+08:00");
            assertThat(candidate.listedPrice()).hasToString("36");
        });
        server.verify();
    }

    @Test
    void givenProviderReturns429_whenFetch_thenItDoesNotRetry() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://netstart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("https://netstart.test/cinema/shows?ci=70&cinemaId=c1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        NetStartShowtimeProvider provider = provider(builder.build());

        ExternalShowtimeProvider.FetchResult result = provider.fetch(LocalDate.of(2026, 8, 7),
                List.of(new ExternalShowtimeProvider.ExternalCinema("c1", "70")));

        assertThat(result.failureCategory()).isEqualTo(ExternalShowtimeProvider.FailureCategory.RATE_LIMITED);
        server.verify();
    }

    @Test
    void givenProviderReturns5xx_whenFetch_thenItRetriesOnceAndReturnsUpstreamFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://netstart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.twice(), requestTo("https://netstart.test/cinema/shows?ci=70&cinemaId=c1"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        NetStartShowtimeProvider provider = provider(builder.build());

        assertThat(provider.fetch(LocalDate.of(2026, 8, 7),
                List.of(new ExternalShowtimeProvider.ExternalCinema("c1", "70"))).failureCategory())
                .isEqualTo(ExternalShowtimeProvider.FailureCategory.UPSTREAM_5XX);
        server.verify();
    }

    @Test
    void givenProviderDisabled_whenFetch_thenItDoesNotCreateAnExternalRequest() {
        NetStartProperties properties = new NetStartProperties(false, false, "https://netstart.test", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(1));
        NetStartShowtimeProvider provider = new NetStartShowtimeProvider(properties,
                new MockEnvironment().withProperty("spring.profiles.active", "dev"), CLOCK,
                RestClient.builder().baseUrl("https://netstart.test").build());

        assertThat(provider.fetch(LocalDate.of(2026, 8, 7),
                List.of(new ExternalShowtimeProvider.ExternalCinema("c1", "70"))).failureCategory())
                .isEqualTo(ExternalShowtimeProvider.FailureCategory.PROVIDER_DISABLED);
    }

    @Test
    void givenMissingSeqNo_whenFetch_thenItQuarantinesTheRecordInsteadOfBuildingCompositeId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://netstart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://netstart.test/cinema/shows?ci=70&cinemaId=c1"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"success":true,"data":{"movies":[{"id":101,"shows":[{"showDate":"2026-08-07",
                        "plist":[{"tm":"11:30","vipPrice":"36"}]}]}]}}"""));
        NetStartShowtimeProvider provider = provider(builder.build());

        assertThat(provider.fetch(LocalDate.of(2026, 8, 7),
                List.of(new ExternalShowtimeProvider.ExternalCinema("c1", "70"))).candidates()).isEmpty();
        server.verify();
    }

    private static NetStartShowtimeProvider provider(RestClient restClient) {
        return new NetStartShowtimeProvider(new NetStartProperties(true, false, "https://netstart.test", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(1)),
                new MockEnvironment().withProperty("spring.profiles.active", "dev"), CLOCK, restClient);
    }
}
