# 设计

## 数据来源与边界

B 的 `AgentRunStatus` 已包含 `WAITING_LOCATION`。用户运行查询在该状态下仍属于活动运行，不得当作完成、失败或可创建第二条运行。C 只消费 B 的状态，不改变位置等待、恢复和超时规则。

## 用户工作区状态

- `AgentRunStatus` 和 `AgentWorkspaceStatus` 增加 `WAITING_LOCATION`。
- 运行快照恢复为 `WAITING_LOCATION` 时不自动重连 SSE，因为 B 当前只有 `RUNNING` 表示正在流式执行；页面仍将它视为活动状态，禁用消息输入并显示取消入口。
- 取消继续调用原 `runId` 的既有取消接口，不创建新请求或新运行。
- 不实现位置上传按钮；这属于 B/C/D 的距离推荐交互范围，不在本变更中猜测。

## 历史消息兼容

- B 的历史消息 DTO 固定输出 `payload`，普通用户 `TEXT` 消息允许值为 `null`。前端 DTO 保留该空值，不伪造业务字段。
- 历史投影读取卡片类型、确认 `actionId` 和状态时，只把空 payload 当作空对象做安全判断；SSE 卡片事件和确认卡仍要求完整对象并继续走白名单校验。
- 精简历史 `QUESTION` 只恢复 `text`，不根据不完整 payload 重建可交互问题卡，避免刷新后重复提交旧问题。

## 最新基线复用

管理端投影、筛选、可空字段和安全展示直接复用最新 `origin/dev` 中已合入的 PR #177。本变更不覆盖或复制这些文件。

## 测试

- Agent 合同测试覆盖 `WAITING_LOCATION + planId=null + planVersion=null`。
- Hook/工作区测试覆盖等待位置恢复后不重复建流、禁用消息和保留取消入口。
- 合同、投影和 Hook 测试覆盖 `payload=null` 用户文本、精简历史问题，以及字符串和数组 payload 拒绝。

## 回退

回退只撤销用户工作区联合类型和页面文案，不影响 B 的运行数据、数据库、PR #177 管理轨迹或其他前端流程。
