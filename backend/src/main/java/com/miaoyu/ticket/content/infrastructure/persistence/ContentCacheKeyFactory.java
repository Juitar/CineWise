package com.miaoyu.ticket.content.infrastructure.persistence;

import com.miaoyu.ticket.content.application.ContentQuery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 构造规定格式的内容缓存键，避免调用方各自拼接城市和查询条件。
 *
 * <p>键中的城市使用规范化后的 cityCode；没有城市条件时使用 all，保证同一全国查询不会产生多份缓存。</p>
 *
 * <p>列表和关键字查询不能直接把关键词放入 Redis 键。键会出现在排障信息中，哈希可以避免暴露用户输入，
 * 同时仍让相同查询稳定命中同一条内容。</p>
 *
 * <p>内容 ID 已是业务标识，按原值写入能方便精准失效。资源类型始终在键中，防止影片和影院使用同一 ID
 * 时相互覆盖。</p>
 */
final class ContentCacheKeyFactory {

    private ContentCacheKeyFactory() {
    }

    /**
     * 有内容 ID 时直接使用；列表和关键字查询使用 SHA-256，避免缓存键泄露原始搜索词。
     *
     * <p>该方法只构造键，不访问 Redis，因此可被缓存和快照适配器共同使用而不会引入基础设施依赖。</p>
     */
    static String create(ContentQuery query) {
        String city = query.cityCode() == null ? "all" : query.cityCode();
        String idOrHash = query.contentId() == null ? hash(city + "|" + query.keyword()) : query.contentId().toString();
        return "ext:content:" + city + ":" + query.resourceType().name().toLowerCase() + ":" + idOrHash;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
