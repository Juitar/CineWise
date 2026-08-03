package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.List;

/**
 * 版本化 Demo 内容资源的标准化表示，供内容种子和后续 Provider 复用。
 *
 * <p>目录只保存来源 ID 和内容字段，不保存数据库 `movieId/cinemaId`。首次初始化的内部 ID
 * 由服务生成，重复初始化通过来源 ID 查回，避免把某个演示库的主键带到另一个环境。</p>
 *
 * <p>`source` 和 `sourceType` 随目录一起保存，使页面、推荐和测试能明确知道数据是 Mock，
 * 不会把固定演示内容误称为外部实时数据。</p>
 */
public record DemoContentCatalog(
        String version,
        String source,
        ContentSourceType sourceType,
        List<MovieContent> movies,
        List<CinemaContent> cinemas) {

    /**
     * 防御性复制防止 Provider 或调用方在加载后修改目录顺序，破坏固定 Demo 的可复现性。
     */
    public DemoContentCatalog {
        movies = List.copyOf(movies);
        cinemas = List.copyOf(cinemas);
    }
}
