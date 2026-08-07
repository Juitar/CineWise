package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 批量解析 Provider 外部身份，保证输入校验、去重顺序和单项失败互不影响。
 * 这里只做身份查询，不创建场次、价格、库存或订单。
 */
@Service
public class ContentIdentityResolutionService {

    private final ContentIdentityResolutionPort delegate;

    public ContentIdentityResolutionService(ContentIdentityResolutionPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    public ContentIdentityResolutionPort.ResolutionBatch resolve(
            String provider, ContentResourceType resourceType, List<String> externalIds) {
        // provider 是来源命名空间的一部分，同一个 externalId 在不同 Provider 下不能混用。
        // 外部身份只按稳定 ID 解析，不能以片名、影院名或地址补猜。
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null || normalizedProvider.length() > 64 || resourceType == null
                || externalIds == null || externalIds.isEmpty() || externalIds.size() > 100) {
            // 整批参数格式错误时没有可靠的逐项语义，按公共参数错误直接拒绝。
            throw new BusinessException(IdentityErrorCode.INVALID_PARAMETER);
        }
        // 上限与 A 的批量调用约定一致，防止一次请求生成过大的 IN 查询。
        List<String> normalizedIds = new ArrayList<>();
        for (String externalId : externalIds) {
            // trim 只消除调用方误传的首尾空白，不改变第三方 ID 的主体。
            String normalized = normalize(externalId);
            if (normalized == null || normalized.length() > 128) {
                // 一个错误 ID 不允许带着其它 ID 部分执行，避免调用方误认为整批结果完整。
                throw new BusinessException(IdentityErrorCode.INVALID_PARAMETER);
            }
            normalizedIds.add(normalized);
        }
        // LinkedHashSet 保留首次出现顺序，调用方可把每项结果对应回原候选。
        List<String> distinctIds = new ArrayList<>(new LinkedHashSet<>(normalizedIds));
        ContentIdentityResolutionPort.ResolutionBatch resolved =
                delegate.resolve(normalizedProvider, resourceType, distinctIds);
        // 包装一次不可变 Batch，防止 Adapter 返回可修改集合穿透到跨模块调用方。
        // 端口只返回固定解析状态，Provider 原始字段和持久化结构不离开 D 模块。
        return new ContentIdentityResolutionPort.ResolutionBatch(resolved.results());
    }

    private static String normalize(String value) {
        // 空身份没有可恢复语义，必须作为参数错误而不是“不存在映射”。
        return value == null || value.isBlank() ? null : value.trim();
    }

    enum IdentityErrorCode implements ErrorCode {
        // 参数错误与单项身份问题分开，调用方可据此决定整批拒绝还是隔离候选。
        INVALID_PARAMETER(100001, "内容身份参数不合法", HttpStatus.BAD_REQUEST),
        CONTENT_IDENTITY_NOT_FOUND(303005, "内容身份未找到", HttpStatus.UNPROCESSABLE_ENTITY),
        CONTENT_IDENTITY_AMBIGUOUS(303006, "内容身份存在歧义", HttpStatus.UNPROCESSABLE_ENTITY),
        CONTENT_IDENTITY_INVALIDATED(303007, "内容身份已失效", HttpStatus.UNPROCESSABLE_ENTITY);

        private final int code;
        private final String message;
        private final HttpStatus status;

        /** 每种单项失败都可由 A 隔离该候选，不影响其它排期候选。 */
        IdentityErrorCode(int code, String message, HttpStatus status) {
            this.code = code;
            this.message = message;
            this.status = status;
        }

        /** 公共响应只读取数字码，不把异常类型或 Provider 信息暴露给调用方。 */
        @Override public int code() { return code; }
        @Override public String message() { return message; }
        @Override public HttpStatus httpStatus() { return status; }
    }
}
