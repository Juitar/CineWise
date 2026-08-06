## Purpose

固定推荐工具结果和 `PLAN_CARD` 的业务字段，使 B 能安全发送、C 能稳定展示，且动态场次、画像和路线依据不会被 Agent 或页面补造。

## ADDED Requirements

### Requirement: PLAN_CARD 必须使用固定推荐字段

系统 SHALL 使用 `schemaVersion=1.0`、`algorithmVersion`、`planCount`、`plans`、`missingFactors`、可空 `relaxationSuggestion`、`usedProfile`、`source`、`dataAt`、`expiresAt` 和 `degraded` 构成推荐卡片载荷。每个方案 MUST 包含 `planType`、影片/影院名称、影片/影院/场次字符串 ID、两位小数字符串 `price`、`currency=CNY`、开场时间、分数、理由、`source`、`dataAt`、`expiresAt`、`isExpired` 和 `purchaseEligible`；路线可用时才可附加距离和预计路程。B 不得修改 D 计算出的动态字段，C 不得把缺失字段补成可购事实。

#### Scenario: 空方案卡片
- **WHEN** 全部候选被硬过滤
- **THEN** 工具返回 `status=SUCCESS` 和空 `plans`
- **AND** 卡片只展示一项放宽建议或无可购场次提示，不显示虚构票价、场次或购票入口

### Requirement: 卡片必须展示来源、时效和画像采用说明

系统 SHALL 将 `ToolResult.dataAt`、`ToolResult.expiresAt`、降级信息和 `usedProfile` 传递给卡片消费者。候选或路线过期时，卡片 MUST 返回 `isExpired=true` 并显示“推荐已过期，请重新查询”，禁用选择和购票入口。

#### Scenario: 场次已经过期
- **WHEN** 卡片发送前场次有效期早于当前业务时间
- **THEN** 系统不发送该方案的可购卡片
- **AND** B 仅按已确认的重新查询流程处理
