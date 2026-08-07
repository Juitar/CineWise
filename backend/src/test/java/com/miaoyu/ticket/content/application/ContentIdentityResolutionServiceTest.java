package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentIdentityResolutionServiceTest {

    @Test
    void givenRepeatedIds_whenResolve_thenItKeepsFirstOccurrenceOrderAndDeduplicates() {
        RecordingPort delegate = new RecordingPort();
        ContentIdentityResolutionService service = new ContentIdentityResolutionService(delegate);

        ContentIdentityResolutionPort.ResolutionBatch result = service.resolve(
                " NETSTART_MAOYAN ", ContentResourceType.MOVIE, List.of(" 11 ", "11", "12"));

        assertThat(delegate.ids).containsExactly("11", "12");
        assertThat(result.results()).extracting(ContentIdentityResolutionPort.Resolution::externalId)
                .containsExactly("11", "12");
    }

    @Test
    void givenInvalidBatch_whenResolve_thenItRejectsBeforeDelegateAccess() {
        RecordingPort delegate = new RecordingPort();
        ContentIdentityResolutionService service = new ContentIdentityResolutionService(delegate);

        assertThatThrownBy(() -> service.resolve("provider", ContentResourceType.CINEMA, List.of()))
                .isInstanceOf(com.miaoyu.ticket.common.error.BusinessException.class)
                .extracting("errorCode.code").isEqualTo(100001);
        assertThat(delegate.ids).isEmpty();
    }

    private static final class RecordingPort implements ContentIdentityResolutionPort {
        private List<String> ids = List.of();

        @Override
        public ResolutionBatch resolve(String provider, ContentResourceType resourceType, List<String> externalIds) {
            ids = externalIds;
            return new ResolutionBatch(externalIds.stream()
                    .map(id -> new Resolution(id, 1L, ResolutionStatus.RESOLVED)).toList());
        }
    }
}
