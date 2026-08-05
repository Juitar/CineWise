package com.miaoyu.ticket.content.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * D 提供给票务和演示种子编排的公开内容查询边界。
 *
 * <p>A 只能通过该端口取得影片展示摘要和已标准化的内容 ID，不能访问 D 的 Controller、缓存、
 * Mapper 或内容表。这样排片可以引用真实影院资料，同时仍明确保持为 A 管理的演示票务数据。</p>
 */
public interface ContentPurchaseQueryPort {

    /** 批量取得影片展示摘要；未找到或不可展示的 ID 不进入结果。 */
    Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds);

    /**
     * 查找长沙真实内容中可用于演示购票的一家影院和最多三部影片。
     *
     * <p>没有完整 LIVE 内容时返回空，调用方继续保留固定 Demo 种子，不得把 Mock 内容冒充真实影院。</p>
     */
    Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog();

    /** 票务页面只需要影片 ID、标题和海报，不获得 D 的内部内容对象。 */
    record MovieSummary(long movieId, String title, String posterUrl) {
    }
}
