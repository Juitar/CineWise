package com.miaoyu.ticket.order.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.order.application.RefundRepository;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 将退款记录和JSON影响快照映射为应用层权威快照。
 *
 * <ul>
 *   <li>MyBatis Row不会穿透到Application层；</li>
 *   <li>JSON序列化只发生在持久化边界；</li>
 *   <li>状态字符串统一映射为RefundStatus；</li>
 *   <li>写入和状态更新都检查恰好影响一行；</li>
 *   <li>解析失败视为数据损坏，不使用当前订单值兜底。</li>
 * </ul>
 */
@Repository
public class MybatisRefundRepository implements RefundRepository {

    private final RefundPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisRefundRepository(RefundPersistenceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RefundSnapshot> findByOrderId(long orderId) {
        return Optional.ofNullable(mapper.findByOrderId(orderId)).map(this::toSnapshot);
    }

    @Override
    public Optional<RefundSnapshot> findByUserAndIdempotencyKey(long userId, String idempotencyKey) {
        return Optional.ofNullable(mapper.findByUserAndIdempotencyKey(userId, idempotencyKey))
                .map(this::toSnapshot);
    }

    @Override
    public void insertRequestedRefund(NewRefund refund) {
        // 快照字段由服务端权威数据和规范化请求组成；适配器不重新计算金额或资格。
        // reason和actionId同时保留列值是为了运维查询，幂等比较仍以JSON整体语义为准。
        RefundImpactPayload payload = new RefundImpactPayload(
                refund.clientRequestId(),
                refund.refundReason(),
                refund.actionId(),
                refund.refundAmount(),
                refund.orderVersionAtRequest(),
                refund.ticketVersionAtRequest(),
                refund.showStartTime());
        RefundInsertRow row = new RefundInsertRow(
                refund.refundId(),
                refund.refundNo(),
                refund.orderId(),
                refund.userId(),
                refund.idempotencyKey(),
                refund.actionId(),
                refund.refundReason(),
                serialize(payload),
                refund.requestedAt());
        if (mapper.insertRequestedRefund(row) != 1) {
            throw new IllegalStateException("退款记录写入行数异常");
        }
    }

    @Override
    public boolean markProcessing(long refundId, int expectedVersion, LocalDateTime updatedAt) {
        return mapper.markProcessing(refundId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public boolean markSuccess(long refundId, int expectedVersion, LocalDateTime processedAt) {
        return mapper.markSuccess(refundId, expectedVersion, processedAt) == 1;
    }

    /** 读取创建时快照，保证结果恢复和参数比较不依赖可变订单状态。 */
    private RefundSnapshot toSnapshot(RefundSnapshotRow row) {
        RefundImpactPayload payload = deserialize(row.impactSnapshot());
        return new RefundSnapshot(
                row.refundId(),
                row.refundNo(),
                row.orderId(),
                row.userId(),
                row.idempotencyKey(),
                payload.clientRequestId(),
                payload.refundReason(),
                payload.actionId(),
                payload.refundAmount(),
                payload.orderVersion(),
                payload.ticketVersion(),
                RefundStatus.valueOf(row.status()),
                row.requestTime(),
                row.processedTime(),
                row.version(),
                row.updatedAt());
    }

    private String serialize(RefundImpactPayload payload) {
        // 使用Spring统一ObjectMapper继承Java时间和空字段策略，避免维护第二套JSON配置。
        // 序列化异常发生在数据库写入前，事务不会产生半条退款记录。
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("退款影响快照序列化失败", exception);
        }
    }

    private RefundImpactPayload deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            // MySQL返回JSON对象文本；H2兼容模式会将绑定的对象文本包装成JSON字符串。
            // 这里只解包测试数据库额外的一层，快照字段和幂等语义保持完全一致。
            String payloadJson = root.isTextual() ? root.textValue() : json;
            return objectMapper.readValue(payloadJson, RefundImpactPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("退款影响快照不可解析", exception);
        }
    }
}
