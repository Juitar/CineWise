## ADDED Requirements

### Requirement: 重规划和追问只使用本轮允许计划
Supervisor SHALL 在初始规划和每次重规划使用同一份服务端计算的允许 Tool 集合。缺失字段只能由本轮允许且已通过校验、实际会使用的计划节点推导；`travelTaskId`、`runId`、`actionId`、`planId`、`showId`、`movieId`、`cinemaId` 及其他内部或业务引用 ID MUST NOT 生成 `QUESTION`。遇到禁止字段时系统 MUST 返回安全 `TEXT`。

#### Scenario: 出行意图没有可信任务上下文
- **WHEN** 识别为 `TRAVEL` 但当前普通会话不存在服务端已校验且属于当前用户的 `travelTaskId`
- **THEN** 系统不执行或展示 `getTravelAdvice`，不追问任务号，并返回引导用户从已有订单或出行任务入口查看建议的 `TEXT`
