## Purpose

让用户在主动授权定位后，按本次位置到影院的直线距离筛选和排序，而不引入路线服务或位置持久化。

## ADDED Requirements

### Requirement: 直线距离只能在用户主动定位后计算

系统 SHALL 仅在用户主动选择按距离推荐且 C 已取得本次定位授权后，使用 Haversine 公式计算用户经纬度到影院静态经纬度的直线距离。精确位置不得进入 B 的 Command、槽位或模型输入，也不得写入 MySQL、Redis、日志、快照或 Agent 轨迹。

#### Scenario: 用户拒绝定位
- **WHEN** 用户拒绝浏览器定位授权
- **THEN** 系统继续普通城市推荐
- **AND** 不返回距离过滤结果或 `NEAREST` 方案

### Requirement: 一次性位置上传必须兼容公共请求层

系统 SHALL 提供 `POST /api/v1/recommendation/distance-contexts/{distanceContextId}/location`，请求体仅含 `longitude`、`latitude`，当前用户从认证 Cookie 对应的服务端上下文取得。成功时 MUST 返回 HTTP 200 和统一 `Result<null>` JSON，不得返回 HTTP 204 空响应。上下文必须属于当前用户、与可信运行上下文关联、未过期且未使用；坐标范围非法返回 HTTP 400，登录失效返回 401，未知、过期、归属不符上下文统一返回 404，重复提交返回 409。

#### Scenario: 前端成功上传本次坐标
- **WHEN** 登录用户以有效一次性 `distanceContextId` 上传范围合法的经纬度
- **THEN** 系统返回 HTTP 200 和 `Result<null>` 成功响应
- **AND** 坐标只保留到本次推荐消费或短期过期，不进入持久化或日志

### Requirement: 距离筛选和最近方案不得伪装路线距离

系统 SHALL 先按直线距离选取最多 10 家有坐标的最近影院，再查询 A 的可售场次。用户设置 `maxDistanceMeters` 时必须在查询场次前排除超出上限的影院；用户要求距离排序时返回 `NEAREST` 方案。卡片 MUST 标明“直线距离约 X km”，不得返回路线、预计时长或高德来源。

#### Scenario: 距离上限排除影院
- **WHEN** 用户设置 3000 米上限且某影院直线距离为 3001 米
- **THEN** 系统不向 A 查询该影院的可售场次
- **AND** 该影院不进入任何推荐方案

### Requirement: 无坐标和距离失败不得阻断普通推荐

系统 SHALL 将没有有效经纬度的影院排除出距离筛选；当距离上下文不可用时，系统 MUST 保留普通城市推荐并返回距离不可用标识，不得伪装为空结果。

#### Scenario: 所有候选影院无坐标
- **WHEN** 城市候选影院均没有有效坐标
- **THEN** 系统返回普通城市推荐
- **AND** 不返回 `NEAREST` 方案
