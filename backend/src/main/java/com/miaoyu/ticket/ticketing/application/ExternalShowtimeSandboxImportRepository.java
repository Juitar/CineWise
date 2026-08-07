package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** A 本地沙箱场次导入的持久化端口；实现只访问 A 拥有的票务与订单表。 */
public interface ExternalShowtimeSandboxImportRepository {

    /** 查询既有映射；首次并发竞争由 V019 外部三元键唯一约束裁决。 */
    Optional<Long> findMappedShowId(String provider, String externalCinemaId, String externalShowId);

    /** 唯一键冲突后使用当前读取得已经提交的赢家映射。 */
    Optional<Long> findMappedShowIdAfterConflict(String provider, String externalCinemaId, String externalShowId);

    /** 按影院与本地沙箱影厅名幂等取得影厅；不得覆盖既有影厅。 */
    long ensureSandboxAuditorium(AuditoriumRow row);

    /** 创建全新的本地沙箱场次；同影厅同开场冲突必须失败并回滚，不能误绑已有场次。 */
    void insertSandboxShow(ShowRow row);

    /** 批量创建本地库存座位；调用方只能传入新场次的固定布局。 */
    void insertSeats(List<SeatRow> rows);

    /** 写入外部身份到本地场次映射；唯一约束是重复导入的最终防线。 */
    void insertMapping(MappingRow row);

    record AuditoriumRow(long id, long cinemaId, String name, int rowCount, int seatCount, LocalDateTime now) { }

    record ShowRow(long id, long movieId, long cinemaId, long auditoriumId, LocalDateTime startTime,
                   LocalDateTime endTime, String languageVersion, BigDecimal basePrice, LocalDateTime now) { }

    record SeatRow(long id, long showId, String rowNo, String seatNo, String seatLabel, LocalDateTime now) { }

    record MappingRow(long id, String provider, String externalCinemaId, String externalShowId, long showId,
                      String source, LocalDateTime dataAt, LocalDateTime expiresAt, LocalDateTime now) { }
}
