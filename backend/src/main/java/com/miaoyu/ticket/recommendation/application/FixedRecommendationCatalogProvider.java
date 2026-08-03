package com.miaoyu.ticket.recommendation.application;

/**
 * 固定推荐目录读取端口。
 *
 * <p>Application 层只依赖版本化目录，不依赖 JSON 或 Spring 资源读取细节，测试可用内存目录稳定复现。
 * </p>
 */
public interface FixedRecommendationCatalogProvider {

    /** 返回当前唯一的固定候选目录。 */
    FixedRecommendationCatalog load();
}
