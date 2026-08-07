# 设计

## 会话槽位

`agent_session` 使用 A 分配的 V020 新增 `slot_snapshot_json`，值是 B 自有的 JSON 对象，仅包含 `cityCode`、`date`、`ticketCount` 和递增版本号。列默认 `{}`，空对象只表示旧会话或尚未收集到槽位；旧会话读取为空槽位。V020 使用 `CHECK (JSON_TYPE(slot_snapshot_json) = 'OBJECT')` 拒绝数组、字符串和标量，但不限制后续合法字段扩展；绝不改 V008、不建外键或 JSON 索引。

消息提交时，B 在同一个短事务内锁定本人 ACTIVE 会话，先按 `clientRequestId` 查询既有 run；命中原请求时直接返回且不修改槽位。新请求读取上一次 QUESTION 指定的槽位，将本条纯文本按槽位类型校验、规范化，再以 `id + userId + version` 的 CAS 条件更新快照并占用 run；任一步失败都整体回滚。非法回答不写快照并继续返回相同 QUESTION。每次 run 的 `SlotSnapshot` 使用该事务内的候选快照，`context.entry` 仅作为运行临时值，不持久化到会话。

日期必须是 ISO `yyyy-MM-dd` 且不早于业务当天；票数必须是正整数；cityCode 必须是六位数字。当前请求中明确出现与已保存值不同的受控槽位时，以本轮已校验值覆盖对应会话槽位。读取旧快照后缺失槽位继续 QUESTION；会话清空、到期和非 ACTIVE 时，既有生命周期会使该快照不可继续使用，且按既有 30 天清理规则随会话删除。取消和失败不删除已确认的会话条件。

## 卡片 DTO

新增 API DTO 只作为 API 映射层：`AgentCardEventResponse` 保留既有外层字段，payload 使用 sealed 的 `AgentCardPayloadResponse`。QUESTION 的 option/input、PLAN_CARD 的 plan item/relaxation suggestion、BUSINESS_INTENT 的 businessRef 和确认卡展示字段均使用明确嵌套 DTO，由现有持久化 JSON 校验后映射；确认结果沿用已有 `AgentActionResponse`。无法识别的非卡片运行事件只走 `Unknown` 安全降级，原样保留 JSON，不作为 C 的卡片类型。不改变已合入字段名或增加平行 payload 格式。历史消息使用同一 payload 映射，并保留字符串 UUID `runId`。

无法由现有 JSON 确定的确认展示字段不新增。确认卡沿用当前 `actionId/actionType/status/expireAt/displayTitle/displayLines`；确认结果沿用当前 action 状态投影。若 C 要求额外字段，必须由 C/A 明确来源和字段含义后另开 change。

## 一致性与安全

所有会话读取/更新都带当前用户 ID；不从 request、模型、slot 或 DTO 读取 userId。幂等查询、槽位 CAS、用户消息和 run 占用共用同一会话锁短事务，失败请求不能留下槽位修改。工具调用仍在事务外；超时、断线和 SSE 重连不自动重试写操作。

## 测试

覆盖城市、日期、票数依次回答、下一轮复用、非法回答、清空/过期；覆盖五种卡片和历史消息 DTO 映射、夹具以及 UUID runId。最后执行相关 Agent 测试、严格 OpenSpec 校验和一次 `backend/mvnw.cmd verify`。
