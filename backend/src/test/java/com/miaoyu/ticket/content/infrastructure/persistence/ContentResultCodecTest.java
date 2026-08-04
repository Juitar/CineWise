package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ContentResultCodecTest {

    /**
     * 快照列在不同 JDBC 驱动中可能是 JSON 对象或 JSON 字符串；两者都必须恢复为同一 LIVE 内容，
     * 否则同步成功后公开接口会因为数据库方言差异无法读取真实数据。
     */
    @Test
    void shouldReadObjectAndTextualJsonPayloads() throws Exception {
        ContentResultCodec codec = new ContentResultCodec(Jackson2ObjectMapperBuilder.json().build());
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 4, 8, 0);
        ContentResult<List<? extends com.miaoyu.ticket.content.domain.ContentItem>> result = new ContentResult<>(
                List.of(new MovieContent(9_000_001L, "live-movie-1", "LIVE 测试片", "[\"剧情\"]", 100,
                        new BigDecimal("8.8"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), dataTime, dataTime.plusHours(6),
                false, false, null);
        String objectPayload = codec.write(result);
        String textualPayload = new ObjectMapper().writeValueAsString(objectPayload);
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, null, null, "LIVE 测试片");

        assertThat(codec.read(objectPayload, query, ContentFallbackType.SNAPSHOT, false).data()).hasSize(1);
        assertThat(codec.read(textualPayload, query, ContentFallbackType.SNAPSHOT, false).data()).hasSize(1);
    }
}
