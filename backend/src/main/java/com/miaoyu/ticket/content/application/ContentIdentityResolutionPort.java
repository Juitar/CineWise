package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.List;

/**
 * 给票务模块使用的内容身份解析端口，只返回内部业务 ID 和固定失败原因。
 * 不把内容表、映射表或 Provider 的内部字段暴露给调用方。
 */
public interface ContentIdentityResolutionPort {

    /**
     * 解析输入已由 Application Service 做格式、数量和顺序控制。
     *
     * <p>适配器只读取已存在映射，不因未命中而创建或修改任何内容资料。</p>
     */
    ResolutionBatch resolve(String provider, ContentResourceType resourceType, List<String> externalIds);

    /** 批量结果不可变，防止调用方在 A/D 边界修改 D 已给出的解析结论。 */
    record ResolutionBatch(List<Resolution> results) {
        public ResolutionBatch {
            results = List.copyOf(results);
        }
    }

    /** 单项仅使用稳定外部身份和内部业务 ID，不泄漏映射表主键。 */
    record Resolution(String externalId, Long internalContentId, ResolutionStatus status) {
        /** 对外固定错误码，避免调用方依赖 D 的异常类或数据库状态字符串。 */
        public int errorCode() {
            return switch (status) {
                case RESOLVED -> 0;
                case NOT_FOUND -> 303005;
                case AMBIGUOUS -> 303006;
                case INVALIDATED -> 303007;
            };
        }
    }

    /** 状态对应固定业务码，调用方据此隔离候选项而不解析数据库异常。 */
    enum ResolutionStatus {
        RESOLVED,
        NOT_FOUND,
        AMBIGUOUS,
        INVALIDATED
    }
}
