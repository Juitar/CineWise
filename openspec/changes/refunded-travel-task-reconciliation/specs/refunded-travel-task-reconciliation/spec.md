# Refunded Travel Task Reconciliation Specification

## ADDED Requirements

### Requirement: 系统必须有界扫描最近已退款订单

系统 SHALL 每五分钟扫描最近二十四小时内 `status=REFUNDED` 且 `refunded_time` 非空的订单。扫描 SHALL 使用一次任务启动时冻结的业务时间作为窗口上界，并按 `refunded_time ASC, id ASC` 稳定键集分页；每个数据库批次 MUST NOT 超过一百条。

#### Scenario: 多批次扫描

- **GIVEN** 最近二十四小时存在超过一百条 REFUNDED 订单，且部分订单具有相同 `refunded_time`
- **WHEN** 补偿任务执行一轮
- **THEN** 系统以不超过一百条的批次处理到冻结窗口上界
- **AND** 不因分页边界重复或遗漏相同毫秒退款的订单

#### Scenario: 窗口和状态过滤

- **GIVEN** 同时存在窗口外订单、非 REFUNDED 订单和 `refunded_time` 为空的数据
- **WHEN** 补偿任务查询候选
- **THEN** 上述订单均不进入 `ensureTaskCancelled` 调用

### Requirement: 系统必须从权威退款终态重建失效事件

系统 SHALL 在调用 D 前按订单主键重新读取 A 的权威订单，且仅当订单仍为 `REFUNDED`、版本与候选一致时继续。事件 SHALL 使用订单的 `orderId/showId/userId/orderVersion`、原始 `refunded_time`、A 的场次开始时间和 D 公开摘要的 `cinemaArea`；业务 ID 使用十进制字符串，时间使用 Asia/Shanghai 对应偏移，`invalidReason` 固定为 `REFUNDED`。系统 MUST NOT 读取 D 的 Entity、Mapper、Repository 或表，也不得猜测缺失区域。

#### Scenario: 完整上下文可用

- **GIVEN** REFUNDED 订单、场次上下文和未过期影院区域均可用
- **WHEN** A 重建 `OrderInvalidated`
- **THEN** `occurredAt` 等于订单原始退款完成时间
- **AND** A 调用 `TravelTaskApplicationService.ensureTaskCancelled` 取消或返回唯一终态任务

#### Scenario: 状态或版本变化

- **GIVEN** 某候选在扫描后已不再是相同版本的 REFUNDED 订单
- **WHEN** A 在调用 D 前重读订单
- **THEN** A 跳过该候选且不调用 `ensureTaskCancelled`

#### Scenario: 上下文缺失或过期

- **GIVEN** 场次不存在，或影院摘要缺失、过期、区域为空或查询异常
- **WHEN** A 尝试补偿该退款订单
- **THEN** 本次跳过该订单且不伪造事件
- **AND** 下轮扫描仍可重新尝试

### Requirement: 重复、乱序和并发补偿必须收敛为取消终态

系统 SHALL 允许同一退款订单在后续轮次或多个应用实例中重复补偿。A 不保存第二份取消标记；每次均调用 D 的公开幂等入口。补偿 MUST NOT 修改订单、支付、电子票、座位或退款状态。

#### Scenario: 重复执行同一退款窗口

- **GIVEN** 某 REFUNDED 订单已经存在 `CANCELLED` 出行任务
- **WHEN** A 再次扫描并使用新 `eventId` 调用 `ensureTaskCancelled`
- **THEN** D 返回原任务或推进到相同退款版本
- **AND** 数据库中仍只有一个该订单的任务

#### Scenario: 退款先于支付补偿到达

- **GIVEN** PAID 补偿已重读到旧支付版本但尚未调用 D
- **WHEN** 退款先完成并由事件或 REFUNDED 补偿建立 `CANCELLED` 墓碑，随后旧支付补偿调用 `ensureTask`
- **THEN** D 不创建或重新打开可用任务
- **AND** 最终任务保持 `CANCELLED`，订单版本不低于退款完成版本

#### Scenario: 多实例同时取消

- **GIVEN** 两个应用实例同时扫描到同一 REFUNDED 订单
- **WHEN** 两者分别调用 `ensureTaskCancelled`
- **THEN** D 的唯一约束和版本条件只保留一个任务
- **AND** 任务状态不回退，A 不使用进程锁或 Redis 作为正确性依据

### Requirement: 单条失败必须隔离并可观察

系统 SHALL 对每个候选独立处理。某条上下文解析或 D 调用抛出运行时异常时，系统 SHALL 记录不含用户敏感信息的订单 ID、异常类型和任务 traceId，增加失败计数并继续后续候选和批次。报告 SHALL 包含批次数、扫描数、取消确保数、跳过数和失败数。

#### Scenario: 中间候选失败

- **GIVEN** 一批候选中的某一条调用 D 失败
- **WHEN** 补偿任务继续执行
- **THEN** 该条计入失败且后续候选仍被处理
- **AND** 下轮扫描可再次尝试失败订单

#### Scenario: 空窗口

- **GIVEN** 最近二十四小时没有 REFUNDED 订单
- **WHEN** 调度任务执行
- **THEN** 返回零计数报告且不调用 D

### Requirement: 两类补偿必须在安全门通过后共同启用

系统 SHALL 在 REFUNDED 补偿、PAID/退款竞态和真实 MySQL 键集分页验证全部通过后，默认启用 PAID 与 REFUNDED 两类补偿 Job。测试环境 MUST 显式关闭自动调度；运维仍可分别关闭开关进行故障隔离，但不得把关闭 REFUNDED 补偿作为长期正常配置。

#### Scenario: 应用使用默认生产配置启动

- **GIVEN** 未覆盖两类补偿的 enabled 环境变量
- **WHEN** 应用启动
- **THEN** 系统注册 PAID 与 REFUNDED 两个补偿 Job
- **AND** 任一 Job 的单轮失败不改变交易终态，后续轮次仍可恢复
