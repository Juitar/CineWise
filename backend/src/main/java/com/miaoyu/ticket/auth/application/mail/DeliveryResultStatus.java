package com.miaoyu.ticket.auth.application.mail;

/** 公共邮件端口只暴露明确成功、明确失败和结果未知三种状态。 */
public enum DeliveryResultStatus {
    SENT,
    FAILED,
    UNKNOWN
}
