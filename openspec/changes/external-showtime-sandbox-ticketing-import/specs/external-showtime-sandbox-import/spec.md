# 外部排期本地沙箱导入 Specification

## ADDED Requirements

### Requirement: A 只导入已确认的本地沙箱参考候选

系统 SHALL 只通过 D 的公开 Application Port 获取候选。A SHALL 仅导入被 D 标记为本地沙箱参考可用、未过期、未降级、无回退、未开场且具有正 `durationMinutes` 的候选。A MUST NOT 从 `rejectedSnapshots`、外部 Provider、D 的表或持久化层获取导入数据。

#### Scenario: 候选不具备导入资格

- **GIVEN** 候选过期、降级、截断、身份不完整、未标记为本地沙箱参考可用或缺少有效时长
- **WHEN** A 执行导入
- **THEN** A 返回跳过结果
- **AND** A 不写入影厅、场次、座位、映射或订单表

### Requirement: 外部场次导入必须幂等

系统 SHALL 以 `provider + externalCinemaId + externalShowId` 作为 A 本地导入的唯一外部身份，并用数据库唯一约束防止并发重复导入。

#### Scenario: 重复导入同一候选

- **GIVEN** 两次导入请求具有相同外部三元键
- **WHEN** A 导入第二次
- **THEN** A 返回第一次创建的本地场次
- **AND** 不重复创建影厅、场次或座位

### Requirement: 本地沙箱票务事实必须明确标识

系统 SHALL 将 A 创建的影厅、座位、库存、价格和预计结束时间标识为本地沙箱事实。外部 `auditoriumText` 只作展示参考，外部 `listedPrice` 只作参考价格，`durationMinutes` 只用于计算本地预计结束时间。

#### Scenario: 用户查看本地沙箱场次

- **GIVEN** A 已从外部参考候选创建本地场次
- **WHEN** 用户查询场次或进入选座
- **THEN** A 的交易价格、余座、影厅和预计结束时间均来自本地数据
- **AND** 系统不将它们声明为外部真实库存、真实影厅或真实散场时间

### Requirement: 历史交易场次不得被外部刷新改写

系统 SHALL 拒绝用新的外部参考候选改写任何已锁座、已售或存在订单的本地沙箱场次。

#### Scenario: 外部候选发生变化但本地场次已有订单

- **GIVEN** 同一外部三元键已映射到存在订单的本地场次
- **WHEN** A 再次导入不同的候选事实
- **THEN** A 跳过更新并记录可观测原因
- **AND** 已有订单、金额、座位和电子票保持不变

### Requirement: 管理员可以受控手动导入本地沙箱场次

系统 SHALL 提供 `POST /api/v1/admin/ticketing/external-showtimes/import`，由 ADMIN 按一个 ISO 日期及 1 至
100 家影院创建一次导入任务。影院业务 ID SHALL 以十进制字符串传入并在 A 应用层转换为正 `long`；重复影院
ID 在调用 D Port 前去重。该入口 SHALL 复用现有 `/api/v1/admin/**` 的 Spring Security 规则，并在 A 的
应用服务中再次通过 `CurrentUserAccessor` 复核 `RoleCode.ADMIN`。响应只返回 A 的任务 ID 和初始状态，
不返回 D 的原始候选、外部身份或 Provider 响应。

本期 MUST NOT 自动拉取、自动创建导入任务或创建第二条 SecurityFilterChain。恢复器只能重新调度已持久化
且未完成的任务，不能自行构造新的导入范围。

#### Scenario: ADMIN 创建手动导入任务

- **GIVEN** 当前用户具有 ADMIN 角色，且请求包含合法日期和不超过 100 家影院
- **WHEN** 管理员调用手动导入接口
- **THEN** A 持久化 PENDING 任务并立即返回任务 ID
- **AND** 后台 Worker 只通过 D 的公开 Application Port 获取候选并执行既有幂等导入

#### Scenario: 普通用户尝试手动导入

- **GIVEN** 当前用户未登录或角色不是 ADMIN
- **WHEN** 调用手动导入接口或应用服务
- **THEN** 系统返回认证或 403 语义
- **AND** 不调用 D Port，也不写入 A 的票务表

#### Scenario: 管理员查询异步导入结果

- **GIVEN** 管理员已获得任务 ID
- **WHEN** ADMIN 查询 `GET /api/v1/admin/ticketing/external-showtimes/import/{taskId}`
- **THEN** 系统返回 PENDING、RUNNING、SUCCESS、PARTIAL 或 FAILED 及脱敏计数
- **AND** 终态时才返回本地场次 ID 字符串和截断标记

#### Scenario: Worker 失效后的安全恢复

- **GIVEN** 任务处于 RUNNING，且其租约已到期
- **WHEN** A 的恢复器接管该任务
- **THEN** 仅以 status、leaseOwner 和 version 条件更新取得新租约
- **AND** 旧 Worker 不能覆盖新的状态或结果

#### Scenario: 任务到达保留期

- **GIVEN** 任务 `expireAt` 已到期
- **WHEN** A 的清理 Job 扫描任务表
- **THEN** 系统硬删除该任务及其仅用于管理查询的结果
- **AND** 不删除 V019 映射、本地场次、座位或任何交易事实

### Requirement: 真实沙箱排期优先于演示排期

在 dev/demo 演示环境中，系统 SHALL 按影院和业务日期判断排期来源。当天存在 `external-sandbox` 场次时，
场次、可用日期、可用影片和可用影院查询 SHALL 隐藏同影院当天的 `demo-seed` 场次；当天没有真实沙箱
场次时，才允许展示按固定模板生成的 `demo-seed` 场次。该规则不得删除或更新已有交易事实。

#### Scenario: 同影院不同日期来源切换

- **GIVEN** 某影院当天有 `external-sandbox`，另一天没有
- **WHEN** 用户查询两天场次
- **THEN** 有真实沙箱的日期只展示真实沙箱场次
- **AND** 未导入的日期展示 Mock 兜底场次并明确标记为演示排期
