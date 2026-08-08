package com.miaoyu.ticket.content.application;

import java.util.Optional;

/**
 * 内容模块向画像模块提供的最小电影类型查询边界。
 *
 * <p>画像只需要一个可解释的主类型来归一化已支付行为，不能读取内容表、Mapper 或 `genresJson`。
 * 内容不存在、类型为空或内容资料不可用时返回空，使支付后的行为摘要仍可被安全保存。</p>
 */
public interface MovieGenreQueryPort {

    /** 查询正数电影 ID 对应的第一个有效类型；没有可靠类型时返回空。 */
    Optional<String> findPrimaryGenre(String movieId);
}
