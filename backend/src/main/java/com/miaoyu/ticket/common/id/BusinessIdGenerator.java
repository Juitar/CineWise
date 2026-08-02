package com.miaoyu.ticket.common.id;

/** 为应用创建的业务数据生成 BIGINT 雪花主键。 */
@FunctionalInterface
public interface BusinessIdGenerator {

    /**
     * 生成下一个业务主键。
     *
     * @return 全局唯一的正整数主键
     */
    long nextId();
}
