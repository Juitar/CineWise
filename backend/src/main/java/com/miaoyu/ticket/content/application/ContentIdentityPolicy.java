package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 真实 Provider 内容的身份识别规则。
 *
 * <p>名称、地址和评分都会变化，不能作为覆盖依据；只有 Provider、资源类型和非空外部 ID
 * 共同组成稳定身份。相同身份在同一同步批次只保留首条，空 ID 和同一名称对应多个 ID 一律隔离，
 * 由同步记录保留原因供人工复核。</p>
 *
 * <p>名称冲突只在相同 resourceType 内判断：同名电影和影院是不同类别的资料，可以同时保存。
 * 反之，同类型同名却来自不同外部 ID 时不自动合并，因为名称不足以证明它们是同一条内容。</p>
 *
 * <p>本策略只决定一个同步批次中哪些候选项可写入，不查询数据库，也不生成业务 ID。
 * 业务 ID 回填、快照写入和审计统计由 ContentSyncService 在事务内完成。</p>
 */
public final class ContentIdentityPolicy {

    /** 将本批次内容分为可写入与隔离项，绝不按标题或影院名称合并。 */
    public Decision decide(String provider, List<? extends ContentItem> items) {
        Set<String> identities = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<ContentItem> accepted = new java.util.ArrayList<>();
        List<RejectedItem> rejected = new java.util.ArrayList<>();
        for (ContentItem item : items) {
            String externalId = externalId(item);
            String identity = provider + "|" + item.resourceType() + "|" + externalId;
            // 名称只用于识别同一资源类型内的候选冲突；影片和影院同名不代表需要彼此隔离。
            String candidateName = item.resourceType() + "|" + displayName(item);
            if (externalId == null || !identities.add(identity) || !names.add(candidateName)) {
                // 同名不同 ID 也不能自动认定为重复，避免连锁影院和重名影片互相覆盖。
                rejected.add(new RejectedItem(item.resourceType(), externalId, "IDENTITY_REVIEW_REQUIRED"));
                continue;
            }
            accepted.add(item);
        }
        return new Decision(List.copyOf(accepted), List.copyOf(rejected));
    }

    private String externalId(ContentItem item) {
        String value = item instanceof MovieContent movie
                ? movie.sourceMovieId()
                : ((CinemaContent) item).sourceCinemaId();
        return value == null || value.isBlank() ? null : value;
    }
    private String displayName(ContentItem item) {
        return item instanceof MovieContent movie ? movie.title() : ((CinemaContent) item).name();
    }

    /** 被隔离项只记录资源类型、外部 ID 和固定原因，不保存原始 JSON 或用户数据。 */
    public record RejectedItem(ContentResourceType resourceType, String externalId, String reason) { }
    public record Decision(List<ContentItem> accepted, List<RejectedItem> rejected) { }
}
