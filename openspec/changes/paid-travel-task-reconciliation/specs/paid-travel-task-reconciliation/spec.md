# Paid Travel Task Reconciliation Specification

## ADDED Requirements

### Requirement: 系统必须有界扫描最近已支付订单

系统 SHALL 每五分钟扫描最近二十四小时内 `status=PAID` 且 `paid_time` 非空的订单。扫描 SHALL 使用一次任务启动时冻结的业务时间作为窗口上界，并按 `paid_time ASC, id ASC` 进行稳定键集分页；每个数据库批次 MUST NOT 超过一百条。

#### Scenario: 多批次扫描

- **GIVEN** 最近二十四小时存在超过一百条 PAID 订单
- **WHEN** 补偿任务执行一轮
- **THEN** 系统以不超过一百条的批次逐页处理到冻结窗口上界
- **AND** 同一轮不因分页重复或遗漏相同 `paid_time` 的订单

#### Scenario: 窗口和状态过滤

- **GIVEN** 同时存在窗口外订单、非 PAID 订单和 `paid_time` 为空的数据
- **WHEN** 补偿任务查询候选
- **THEN** 上述订单均不进入 `ensureTask` 调用

### Requirement: 系统必须从权威事实重建支付事件

系统 SHALL 在调用 D 前按订单主键重新读取 A 的权威订单，且仅当订单仍为 `PAID`、版本与候选一致时继续。事件 SHALL 使用订单的 `orderId/showId/userId/orderVersion`、原始 `paid_time`、A 的场次开始时间和 D 公开摘要的 `cinemaArea`；业务 ID 使用十进制字符串，时间使用 Asia/Shanghai 对应偏移。系统 MUST NOT 读取 D 的 Entity、Mapper、Repository 或表，也不得猜测缺失区域。

#### Scenario: 完整上下文可用

- **GIVEN** PAID 订单、场次上下文和未过期影院区域均可用
- **WHEN** A 重建 `PaymentSucceededEvent`
- **THEN** `occurredAt` 等于订单原始支付时间
- **AND** A 调用 `TravelTaskApplicationService.ensureTask` 创建或返回唯一任务

#### Scenario: 上下文缺失或过期

- **GIVEN** 场次不存在，或影院摘要缺失、过期、区域为空或查询异常
- **WHEN** A 尝试补偿该订单
- **THEN** 本次跳过该订单且不伪造事件
- **AND** 订单继续保持原交易终态，下轮可以重试

#### Scenario: 扫描后订单状态变化

- **GIVEN** 某候选在扫描后已不再是 PAID，或订单版本已经变化
- **WHEN** A 在调用 D 前重读订单
- **THEN** A 跳过该候选且不调用 `ensureTask`

### Requirement: 重复和并发补偿必须安全

系统 SHALL 允许同一订单在后续轮次或多个应用实例中重复补偿。A 不保存第二份任务完成标记；每次均调用 D 的公开幂等入口，D 以 `orderId` 和唯一约束返回原任务。补偿 MUST NOT 修改订单、支付、电子票、座位或退款状态。

#### Scenario: 重复执行同一窗口

- **GIVEN** 某 PAID 订单已经存在出行任务
- **WHEN** A 再次扫描并使用新 `eventId` 调用 `ensureTask`
- **THEN** D 返回原任务且数据库中仍只有一个该订单的任务

#### Scenario: 多实例同时补偿

- **GIVEN** 两个应用实例同时扫描到同一 PAID 订单
- **WHEN** 两者分别调用 `ensureTask`
- **THEN** D 的唯一约束只保留一个任务
- **AND** A 不使用进程锁或 Redis 作为正确性依据

### Requirement: 单条失败必须隔离并可观察

系统 SHALL 对每个候选独立处理。某条上下文解析或 D 调用抛出运行时异常时，系统 SHALL 记录不含用户敏感信息的订单 ID、异常类型和任务 traceId，增加失败计数并继续后续候选和批次。报告 SHALL 包含批次数、扫描数、确保成功数、跳过数和失败数。

#### Scenario: 中间候选失败

- **GIVEN** 一批候选中的某一条调用 D 失败
- **WHEN** 补偿任务继续执行
- **THEN** 该条计入失败且后续候选仍被处理
- **AND** 下轮扫描可再次尝试失败订单

#### Scenario: 空窗口

- **GIVEN** 最近二十四小时没有 PAID 订单
- **WHEN** 调度任务执行
- **THEN** 返回零计数报告且不调用 D
