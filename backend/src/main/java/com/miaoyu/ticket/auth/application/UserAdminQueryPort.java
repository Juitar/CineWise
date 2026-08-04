package com.miaoyu.ticket.auth.application;

import java.util.Map;
import java.util.Set;

/**
 * C向管理模块公开的最小用户目录查询契约。
 *
 * <p>该接口只冻结A/C已经确认的跨模块类型；用户表查询、邮箱规范化、
 * 脱敏、数量上限、异常转换和ADMIN复核仍全部由C的实现负责。</p>
 *
 * <p>A只能把返回值用于当前管理查询，不能据此建立第二份用户索引，
 * 也不能从其他认证对象补齐完整邮箱或账号状态。</p>
 */
public interface UserAdminQueryPort {

    /**
     * 将一个非空管理筛选词解析为有限用户ID集合。
     *
     * <p>C保证结果不超过100个；第101个匹配会以201010拒绝查询，
     * 数据源不可用则以301002失败，二者都不能伪装成空集合。</p>
     */
    Set<Long> findUserIdsByKeyword(String userKeyword);

    /**
     * 一次批量返回当前页用户的脱敏摘要。
     *
     * <p>Map缺少某个ID表示历史用户不可用，调用方仍须保留订单；
     * 整批查询失败不得返回部分Map。</p>
     */
    Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds);

    /** 只公开管理订单展示必需的用户ID和C统一生成的脱敏邮箱。 */
    record UserAdminSummary(long userId, String emailMasked) {
    }
}
