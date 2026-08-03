package com.miaoyu.ticket.agent.domain.plan;

/** 输入引用只能来自已经声明的槽位或上游节点结果。 */
public enum InputReferenceSource {
    SLOT,
    NODE_RESULT
}
