## 1. 先决检查与白名单

- [x] 1.1 B 核对 `RankMoviePlanTool`、`RankMoviePlanCommand`、`FixedRecommendationResult` 与本 change 的字段、Java 类型和只读边界一致；若 D 已合入接口不一致，停止实现并记录具体文件、字段和 D Owner。验证：编译期类型引用和白名单单元测试。（Owner：B）
- [x] 1.2 B 在 Agent 白名单装配处登记 `rankMoviePlan`：Command/结果类型、只读属性、3 秒时限、幂等要求、五个输入及 `100001` 均与 design 一致。验证：注册表精确查询和不可变性测试。（Owner：B）
- [x] 1.3 B 用该白名单运行 `PlanSchemaValidator`，验证必填槽位、可选时段字段、Java 类型和未知字段仍按既有规则拒绝。验证：新增计划校验测试。（Owner：B）

## 2. 类型化执行适配

- [x] 2.1 B 在 `agent` 的 Application 边界新增窄的 `rankMoviePlan` 执行适配器及其受信任执行输入类型；仅提供当前运行元数据、已校验运行状态和节点 ID，不引入 Controller、SSE、持久化或通用工具路由。验证：架构测试和构造测试。（Owner：B）
- [x] 2.2 B 从已校验节点的 `SLOT` 引用和 `SlotSnapshot` 解析 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`，并构造 `RankMoviePlanCommand`；按 design 处理缺失、格式、成对时段和先后关系。验证：槽位转换正常、边界和拒绝测试。（Owner：B）
- [x] 2.3 B 只通过注入的 `RankMoviePlanTool.execute(ToolContext, RankMoviePlanCommand)` 调用 D；上下文由受信任运行元数据、已校验节点、3 秒上限和槽位快照版本构成。验证：测试断言调用次数、实际 Command 和 Context；无反射、Bean 名、HTTP 或字符串方法调用。（Owner：B）
- [x] 2.4 B 将 D 返回结果和本地 `100001` 参数失败结果都交给 `ExecutionPlanStateMachine.recordToolResult()`；不创建计划、SSE、确认、数据库或 Redis 状态。验证：状态推进和无额外副作用测试。（Owner：B）

## 3. B-D 联合测试

- [x] 3.1 B 使用 D 的真实 `RankMoviePlanTool` 与固定推荐夹具，覆盖合法调用和 `purchaseEligible=false + missingFactors=[SHOWTIME]` 的成功降级；确认状态机均标记节点 `SUCCESS`。验证：B-D 最小集成测试通过。（Owner：B；D 接口无需改动）
- [x] 3.2 B 覆盖槽位缺失、非法业务 ID、非法日期/时间、单侧时段和倒置时段；确认不调用 D，生成 `FAILED + 100001 + retryable=false`，并按状态机跳过下游。验证：适配器单元测试通过。（Owner：B）
- [x] 3.3 B 覆盖 D 返回的可重试和不可重试失败，确认复用既有 `RETRY_ONCE`、最终失败和下游跳过规则。验证：状态机衔接测试通过。（Owner：B）

## 4. 验证和交付

- [x] 4.1 B 执行新增/受影响 Agent 与推荐测试，并记录通过、失败、跳过数。验证：相关 Maven 定向测试通过。（Owner：B）
- [x] 4.2 B 在 `backend/` 执行 `mvnw.cmd verify`。验证：编译、测试、架构检查、Checkstyle、SpotBugs 和 JaCoCo 通过；环境跳过项单独记录。（Owner：B）
- [x] 4.3 B 执行 `openspec validate agent-rank-movie-plan-execution --strict`、`git diff --check` 和变更文件核对。验证：严格校验通过且没有无关文件。（Owner：B）
- [x] 4.4 B 请 D 仅复核实现是否仍严格使用既有 `RankMoviePlanTool.execute` 与结果语义；A、C 无新增确认项。验证：D 已确认未变更，日期：2026-08-04。（Owner：B、D）
