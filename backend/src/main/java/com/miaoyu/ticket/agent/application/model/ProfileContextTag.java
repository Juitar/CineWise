package com.miaoyu.ticket.agent.application.model;

/** 仅供模型计划请求使用的画像标签副本，不含用户身份或持久化主键。 */
public record ProfileContextTag(
        String type, String value, String polarity, String weight, String confidence, String source, String updatedAt) {
}
