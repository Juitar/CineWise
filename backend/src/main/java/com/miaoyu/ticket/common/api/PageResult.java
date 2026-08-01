package com.miaoyu.ticket.common.api;

import java.util.List;

/** REST 统一分页数据；页码从 1 开始。 */
public record PageResult<T>(long total, int page, int size, List<T> records) {

    public PageResult {
        records = List.copyOf(records);
    }
}
