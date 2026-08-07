package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;

/** 从本地内容 ID 反查唯一的 ACTIVE 外部身份，仅供 D 访问 Provider 前定位资源。 */
public interface ContentExternalIdentityLookupPort {

    List<ExternalIdentity> findActiveExternalIds(String provider, ContentResourceType resourceType,
                                                 List<Long> contentIds);

    /** providerCityId 只用于影院的 D 内部 HTTP 调用；影片反查时该值为空。 */
    record ExternalIdentity(Long contentId, String externalId, String providerCityId) { }
}
