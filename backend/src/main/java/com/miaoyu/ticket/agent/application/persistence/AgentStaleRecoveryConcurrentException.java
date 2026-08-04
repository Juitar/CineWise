package com.miaoyu.ticket.agent.application.persistence;

/** 陈旧运行恢复发现已有新写入时触发回滚；调用方保留当前记录，下一轮再按阈值扫描。 */
public class AgentStaleRecoveryConcurrentException extends RuntimeException {
}
