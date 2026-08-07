package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentCityCinemaQueryPort;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 只读查询已同步且未逻辑删除的本地影院，不按名称、地址或坐标猜测影院。 */
@Repository
public class JdbcContentCityCinemaQueryAdapter implements ContentCityCinemaQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentCityCinemaQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Long> findActiveCinemaIds(String cityName) {
        // 该查询不做分页：上游只取 cinemaId，并由 A 的批量上限控制后续请求规模。
        // cityName 来自已加载的本地目录，数据库层无需也不能再次访问 Provider 校验。
        // 只使用 V014 的规范化 city_name；绝不通过地址、区县或旧 city_code 猜测城市归属。
        // 逻辑删除影院不应进入 A 的后续可售场次批量查询。
        // ORDER BY id 让缓存键和测试结果保持稳定。
        return jdbcTemplate.query("""
                SELECT id
                  FROM cinema
                 WHERE city_name = ? AND deleted_at IS NULL
                 -- 只返回内部 cinemaId；影院名称、地址由摘要查询按独立权限读取。
                 ORDER BY id
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), cityName);
    }
}
