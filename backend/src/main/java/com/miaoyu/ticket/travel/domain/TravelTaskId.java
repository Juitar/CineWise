package com.miaoyu.ticket.travel.domain;

import java.util.Objects;

/**
 * 对外暴露的出行任务号。
 *
 * <p>任务号与数据库内部 BIGINT 主键分开，页面、邮件和后续 REST API 只使用字符串，
 * 从而避免 JavaScript 把大整数精度截断。这里不限制生成算法或前缀，生成策略由后续
 * 创建任务用例决定。</p>
 *
 * @param value 非空、去除首尾空白后的任务号
 */
public record TravelTaskId(String value) {

    private static final int MAX_LENGTH = 64;

    /**
     * 校验并规范化任务号。
     *
     * <p>数据库契约限定为 VARCHAR(64)，在领域层先拒绝空值和超长值，避免到持久化阶段
     * 才出现难以定位的截断或约束错误。</p>
     */
    public TravelTaskId {
        value = Objects.requireNonNull(value, "taskId 不能为空").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("taskId 长度不能超过 64");
        }
    }
}
