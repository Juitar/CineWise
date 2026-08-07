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
100 家影院触发一次导入。影院业务 ID SHALL 以十进制字符串传入并在 A 应用层转换为正 `long`；重复影院
ID 在调用 D Port 前去重。该入口 SHALL 复用现有 `/api/v1/admin/**` 的 Spring Security 规则，并在 A 的
应用服务中再次通过 `CurrentUserAccessor` 复核 `RoleCode.ADMIN`。响应只返回 A 本地场次 ID 字符串及
`truncated` 标记，不返回 D 的原始候选、外部身份或 Provider 响应。

本期 MUST NOT 创建 `@Scheduled` 任务、自动拉取、自动导入或第二条 SecurityFilterChain。

#### Scenario: ADMIN 手动导入合格候选

- **GIVEN** 当前用户具有 ADMIN 角色，且请求包含合法日期和不超过 100 家影院
- **WHEN** 管理员调用手动导入接口
- **THEN** A 只通过 D 的公开 Application Port 获取候选并执行既有幂等导入
- **AND** 响应返回本地场次 ID 字符串和是否发生截断

#### Scenario: 普通用户尝试手动导入

- **GIVEN** 当前用户未登录或角色不是 ADMIN
- **WHEN** 调用手动导入接口或应用服务
- **THEN** 系统返回认证或 403 语义
- **AND** 不调用 D Port，也不写入 A 的票务表

#### Scenario: 管理员重复触发相同候选的导入

- **GIVEN** 同一外部三元键已经成功映射到 A 本地场次
- **WHEN** ADMIN 再次触发覆盖该候选的导入
- **THEN** 系统返回已有本地场次 ID
- **AND** 不重复创建影厅、场次或座位
