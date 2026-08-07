package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;

/** 从本地内容 ID 反查唯一的 ACTIVE 外部身份，仅供 D 访问 Provider 前定位资源。 */
public interface ContentExternalIdentityLookupPort {

    /**
     * 只按内部业务 ID 查找外部身份，禁止按名称、地址或坐标猜测映射。
     *
     * <p>调用方会用返回的外部影院 ID 和城市 ID 组装 Provider 请求；没有 ACTIVE 映射时返回空集合，
     * 由上层把该影院从本次排期查询中隔离。</p>
     */
    List<ExternalIdentity> findActiveExternalIds(String provider, ContentResourceType resourceType,
                                                 List<Long> contentIds);

    /** providerCityId 只用于影院的 D 内部 HTTP 调用；影片反查时该值为空。 */
    record ExternalIdentity(Long contentId, String externalId, String providerCityId) { }
}
