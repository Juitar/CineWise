# Agent 多轮槽位与卡片接口

## 背景

当前 Agent 每轮仅把 `context.entry` 作为临时槽位传入运行；城市、日期和票数在 QUESTION 后不会保存到会话，下一轮仍会重复追问。C 已能消费部分 SSE 卡片，但历史消息、QUESTION、PLAN_CARD、BUSINESS_INTENT 和确认卡/结果没有同一套正式 DTO 映射入口。

## 本次修改

- B 在本人 ACTIVE 会话保存已校验的 `cityCode`、`date`、`ticketCount`，后续运行复用仍有效的值。
- 为这些槽位增加 B 自有 `agent_session.slot_snapshot_json` 持久化列；不新增表，不修改已有迁移。
- 把五类卡片和历史消息统一映射为 C 可直接渲染的 DTO，保持既有 SSE 外层 `eventType/eventId/runId/payload` 不变。
- 补齐后端映射测试与 C 联调夹具。

## 不做

- 不实现天气、路线、定位、距离推荐、退票或新的确认业务。
- 不改 C 前端私有实现、A 票务实现、D 推荐/画像/天气实现，也不改 Cookie、CSRF、心跳或重连。

## 影响与负责人

- B：会话槽位、运行输入、卡片 DTO/映射、测试和迁移草案。
- A：已分配 V020，待静态审查 `agent_session` 新列及 JSON 顶层对象约束。
- C：按本次 DTO/夹具消费，不需要查询历史消息猜卡片字段。

## 验收

- 三个槽位依次回答后，下一轮不重复 QUESTION 且真实运行读取已保存值。
- 非法、冲突、取消、过期按既有运行生命周期处理。
- QUESTION、PLAN_CARD、BUSINESS_INTENT、确认卡、确认结果都有受控 DTO、映射和夹具；历史消息 `runId` 为 UUID 字符串。
