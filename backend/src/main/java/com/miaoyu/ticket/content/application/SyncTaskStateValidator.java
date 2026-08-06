package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;

/**
 * 管理员同步任务写入前的统一状态校验。
 *
 * <p>V014 为兼容历史记录暂未在数据库收紧所有状态组合；因此新 Writer 自己必须拒绝矛盾数据，
 * 防止一次 Provider 结果被错误地写成“部分失败但没有失败原因”。后续 V015 只是在数据库再加一道保护，
 * 不能替代这里的业务校验。</p>
 */
public final class SyncTaskStateValidator {
    private SyncTaskStateValidator() { }

    /** PENDING 尚未执行，不能预先写入计数、错误或完成时间。 */
    public static void validatePending(ContentSyncTaskPort.SyncTask task) {
        if (task.status() != ContentSyncTaskPort.SyncTaskStatus.PENDING || task.successCount() != 0
                || task.failureCount() != 0 || task.finishedAt() != null || task.failureCategory() != null) {
            throw new IllegalArgumentException("PENDING 同步任务状态不合法");
        }
    }

    /** RUNNING 必须绑定未过期的随机持有者，避免无租约任务访问 Provider。 */
    public static void validateRunning(String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
        if (!hasLeaseOwner(leaseOwner) || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("RUNNING 同步任务必须持有有效租约");
        }
    }

    /**
     * 终态必须同时校验计数、错误字段和失败分类。
     *
     * <p>FAILED 允许三项计数都为零：这表示 Provider 尚未获得候选项就失败；其他终态则保持完整的
     * 成功/失败统计，便于管理员页面准确显示。</p>
     */
    public static void validateTerminal(ContentSyncTaskPort.SyncTaskStatus status, int totalCount, int successCount,
                                        int failureCount, Integer errorCode,
                                        ContentSyncTaskPort.FailureCategory failureCategory,
                                        LocalDateTime finishedAt) {
        if (finishedAt == null || totalCount < 0 || successCount < 0 || failureCount < 0
                || successCount + failureCount > totalCount) {
            throw new IllegalArgumentException("同步任务计数或完成时间不合法");
        }
        switch (status) {
            case SUCCESS -> require(successCount == totalCount && failureCount == 0
                    && errorCode == null && failureCategory == null, "SUCCESS 同步任务状态不合法");
            case FAILED -> require(successCount == 0 && (totalCount == 0 || failureCount == totalCount)
                    && errorCode != null && failureCategory != null, "FAILED 同步任务状态不合法");
            case PARTIAL -> require(successCount > 0 && failureCount > 0 && successCount + failureCount == totalCount
                    && errorCode != null && failureCategory != null, "PARTIAL 同步任务状态不合法");
            case PENDING, RUNNING -> throw new IllegalArgumentException("只能完成终态同步任务");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 终态条件更新仍要求写入方给出持有者，是否过期由 SQL 的 WHERE 条件判定。 */
    public static void validateLeaseOwner(String leaseOwner) {
        if (!hasLeaseOwner(leaseOwner)) {
            throw new IllegalArgumentException("同步任务租约持有者不合法");
        }
    }

    private static boolean hasLeaseOwner(String leaseOwner) {
        return leaseOwner != null && !leaseOwner.isBlank();
    }
}
