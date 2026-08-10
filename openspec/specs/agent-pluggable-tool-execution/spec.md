# agent-pluggable-tool-execution Specification

## Purpose
定义 Agent 只执行已注册工具、处理工具失败和将工具状态映射为安全事件的规则。
## Requirements
### Requirement: 仅执行已注册的类型化工具
系统 SHALL 通过 `ToolRegistry` 和类型化 executor 注册项选择工具；计划中的工具名、Command 类型、输入引用和白名单任一不匹配时，不得调用业务模块或反射目标。

#### Scenario: 已注册的多个只读工具按依赖执行
- **WHEN** 候选计划通过校验并包含多个依赖关系满足的已注册只读工具节点
- **THEN** 系统按状态机选择顺序执行每个节点一次，并返回各节点的统一 `ToolResult`

#### Scenario: 未注册工具或参数不匹配
- **WHEN** 计划引用未登记工具、未知输入或错误输入类型
- **THEN** 系统拒绝计划且不执行任何工具

### Requirement: 工具失败重规划不重放写操作
系统 SHALL 只在已执行的只读工具返回 `FAILED` 且 `replanSuggested=true` 时，在服务器限制内生成新计划；写工具、超时未知结果、SSE 重连和断线不得自动重试。

#### Scenario: 只读工具建议重新规划
- **WHEN** 已注册只读工具返回失败并建议重新规划，且未达到计划版本上限
- **THEN** 系统请求并校验新计划，保留原运行与客户端请求标识，并将新版本交给 CAS 持久化

#### Scenario: 确认后的建单结果未知
- **WHEN** `createOrder` 调用超时或连接中断导致结果未知
- **THEN** 系统使用原 actionId 和幂等键查询结果，不创建新 actionId 或自动再次建单

### Requirement: 未确认业务接口可先接入 Mock
系统 SHALL 为 `queryAvailableDates`、`queryShows`、`querySeats` 提供统一注册接口、Mock executor 和夹具；其中 `querySeats` 仅作为可插拔扩展位，不属于本 change 的关键路径验收。未接入正式公开 API、DTO 和字段前，不得实现猜测的生产 Command 或直接访问业务内部层。

#### Scenario: 正式业务接口尚未提供
- **WHEN** B 装配 Agent 核心流程且 A 的查询 Tool 尚未注册生产实现
- **THEN** 系统只允许测试夹具中的 Mock 注册，生产白名单不暴露该工具

#### Scenario: 选座走购票页
- **WHEN** 场次结果已由工具校验且用户进入选座阶段
- **THEN** Agent 输出 `card` + `BUSINESS_INTENT`/`SELECT_SEATS`，在 `businessRef` 中携带同一场次的 `showId`、`movieId`、`cinemaId`；三者均为无前导零、Java `long` 范围内的正十进制字符串；不调用 `querySeats`

### Requirement: 受控槽位执行出行建议 Tool
系统 SHALL 将已注册的 `getTravelAdvice` 作为 `readOnly=true`、无需确认的生产 Tool。执行 Adapter MUST 只调用 D 的 `GetTravelAdviceTool.execute(ToolContext, GetTravelAdviceCommand)`，命令任务号 MUST 只来自当前执行节点单一的 `SLOT/travelTaskId` 引用；Adapter 不得接收 `userId`、调用 D 的 Controller/Repository/Entity/Mapper 或解析内部 JSON。

#### Scenario: 受控任务槽位查询成功
- **WHEN** 当前运行节点使用已确认的 `travelTaskId` 槽位且 D Tool 成功返回结构化结果
- **THEN** Adapter 调用一次 D 的公开 Tool，Supervisor 将成功结果交给出行建议卡片映射，并且不产生确认动作或写操作

#### Scenario: 模型伪造任务输入被拒绝
- **WHEN** 节点缺少槽位、包含多个输入引用，或任务号引用不是 `SLOT/travelTaskId`
- **THEN** Adapter 在调用 D 前返回现有安全参数错误，且不读取或泄露任何出行任务

#### Scenario: D Tool 查询失败
- **WHEN** D Tool 返回任务不存在、无权限、过期查询失败或其他失败结果
- **THEN** Supervisor 使用既有安全错误映射，不泄露任务归属、内部异常、原始 JSON 或其他用户信息
