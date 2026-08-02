package com.miaoyu.ticket.content.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 内容模块固定演示数据的持久化端口。 */
public interface ContentSeedRepository {

    /** 按来源与外部影片 ID 幂等创建影片，并返回数据库中的权威主键。 */
    long ensureMovie(MovieSeed row);

    /** 按来源与外部影院 ID 幂等创建影院，并返回数据库中的权威主键。 */
    long ensureCinema(CinemaSeed row);

    record MovieSeed(
            long id,
            String sourceMovieId,
            String title,
            String genresJson,
            int durationMinutes,
            BigDecimal rating,
            LocalDateTime dataTime,
            LocalDateTime expiresAt) {
    }

    record CinemaSeed(
            long id,
            String sourceCinemaId,
            String name,
            String cityCode,
            String area,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            LocalDateTime dataTime,
            LocalDateTime expiresAt) {
    }
}
