package com.miaoyu.ticket.agent.domain.plan;

/** 一个工具输入字段使用的服务端可验证来源。 */
public record InputReference(String inputName, InputReferenceSource source, String sourceId) {
}
