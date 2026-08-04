package com.miaoyu.ticket.travel.domain;

/**
 * 出行任务的乐观锁版本号。
 *
 * <p>后续生成或刷新建议时，会以该值做条件更新并追加同版本的快照。此值对象只维护
 * 非负和递增规则，不承担数据库更新或并发重试，避免领域基础类型依赖持久化实现。</p>
 *
 * @param value 从零开始的任务版本
 */
public record TravelTaskVersion(long value) {

    /**
     * 版本号不可为负，和 V007 的 CHECK 约束保持一致。
     */
    public TravelTaskVersion {
        if (value < 0) {
            throw new IllegalArgumentException("taskVersion 不能为负数");
        }
    }

    /**
     * 返回下一版任务号，不改变当前不可变值对象。
     *
     * <p>使用 {@link Math#incrementExact(long)} 让极端溢出显式失败，不能让版本回绕为负数。
     * 正常业务不会到达该值，仍保留这个保护以维持版本单调递增。</p>
     *
     * @return 加一后的任务版本
     */
    public TravelTaskVersion next() {
        return new TravelTaskVersion(Math.incrementExact(value));
    }
}
