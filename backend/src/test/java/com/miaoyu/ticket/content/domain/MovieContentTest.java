package com.miaoyu.ticket.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MovieContentTest {

    @Test
    void givenInvalidReleaseFields_whenCreatingMovie_thenTheyAreNotExposed() {
        MovieContent movie = new MovieContent(null, "source-1", "测试影片", "[\"剧情\"]", 100,
                new BigDecimal("8.0"), null, null, "2026/08/01", "UNKNOWN");

        // Provider 字段即使绕过 Mapper，也不能将非法日期或未知枚举写入缓存、快照和 REST DTO。
        assertThat(movie.releaseDate()).isNull();
        assertThat(movie.releaseStatus()).isNull();
    }
}
