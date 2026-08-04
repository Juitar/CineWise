package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.travel.domain.TravelTaskStatus;

/** A 的补偿调用和后续只读查询共用的最小任务结果。 */
public record TravelTaskSummary(String taskId, TravelTaskStatus status, long orderVersion) {
}
