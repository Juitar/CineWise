package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import java.util.List;
import java.util.Set;

/**
 * 候选真实 Provider 向同步用例提供已标准化的内容，不暴露 HTTP 或 JSON。
 *
 * <p>该端口只给定时同步使用，页面查询只能读取缓存、快照或 Demo。这样用户请求不会因网络超时、
 * 第三方限流或字段变化而直接失败，也不会把外部 Provider 变成页面的隐式依赖。</p>
 */
public interface LiveContentSyncPort {
    /**
     * 返回本轮已标准化内容及不含原始载荷的审计结论。
     *
     * <p>关闭 Provider、限流、超时或字段校验失败都必须显式说明，不能只用空列表掩盖原因。Application
     * 据此写入 `data_sync_log`，页面仍只会读取已有快照或 Demo，不会因此发起第二次外部调用。</p>
     */
    DailySyncBatch fetchForDailySync();

    /**
     * 拉取当前热映目录中尚未成功保存详情的影片。
     *
     * <p>V014 尚未引入专用队列表，因此由调用方传入已落库的稳定外部身份。Provider 每次重新读取
     * 当前目录后跳过这些身份，只处理当前分钟预算内的其余项；下一次任务会自然从剩余身份继续，
     * 不需要在 HTTP 线程中等待下一分钟。</p>
     */
    default DailySyncBatch fetchCurrentHotMovies(Set<String> completedSourceMovieIds) {
        // 旧的单方法测试替身仍可用于回归；正式 NetStart 适配器会覆盖为按已完成身份恢复的影片批次。
        return fetchForDailySync();
    }

    /** 单项同时携带规范化查询键和 LIVE 内容封套，以便只替换对应的真实快照与缓存。 */
    record SynchronizedContent(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }

    /** 本轮尝试数量包含被字段校验拒绝的项，供审计区分“没有内容”和“内容不可用”。 */
    record DailySyncBatch(List<SynchronizedContent> contents, int attemptedCount, Outcome outcome,
                          Integer errorCode) {
        public DailySyncBatch {
            contents = List.copyOf(contents);
            if (attemptedCount < contents.size()) {
                throw new IllegalArgumentException("attemptedCount must include accepted content");
            }
        }
    }

    /** 只保留固定分类和 HTTP 状态码，避免审计日志写入上游响应正文或密钥。 */
    enum Outcome {
        SUCCESS,
        PROVIDER_DISABLED,
        CONNECTION_FAILED,
        RATE_LIMITED,
        UPSTREAM_FAILED,
        FIELD_REJECTED
    }
}
