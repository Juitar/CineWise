## 1. 规划与边界（B）

- [x] 1.1 核对最新 `origin/dev` 的工具、确认建单接口、运行版本、CAS 和恢复规则；验证：记录于 proposal/design。
- [x] 1.2 创建 proposal、spec、design 和任务清单并严格校验；验证：`openspec validate agent-multi-tool-orchestration-and-replanning --strict`。

## 2. 多工具规则与调度（B）

- [x] 2.1 扩展工具定义和计划校验，使已登记写工具只能进入确认等待状态；验证：白名单、未知工具、写节点和输入引用单元测试（22 项相关测试通过）。
- [x] 2.2 实现多个只读工具的类型化执行注册与 Supervisor 调度；验证：类型化适配器列表、无依赖节点和重复执行保护测试通过。D 确认当前生产白名单只启用 `rankMoviePlan`；其他 D 工具待单独确认后再登记。
- [x] 2.3 接入已合入的确认建单公开能力，但不修改其 action/恢复实现；验证：等待确认步骤落库后才调用既有 action 创建服务；旧 action 不触发写调用，相关测试通过。

## 3. 重规划、持久化与恢复（B）

- [x] 3.1 实现两次上限、成功分支保留、失败分支替换/跳过和计划版本提升；验证：`AgentRunStateMachineTest` 与 `MultiToolSupervisorTest` 共 15 项通过。
- [x] 3.2 以运行 CAS 保存重规划版本并拒绝旧计划事件/迟到结果；验证：重规划 CAS 测试、计划版本测试和旧计划迟到事件拒绝测试通过。
- [x] 3.3 覆盖只读重试、不可重试、PROCESSING、中断、结果未知、SSE 断线和写工具不自动重试；验证：状态机、确认恢复、取消和 `SseDisconnectHandlerTest` 通过。

## 3.4 V015 迁移审查（B / A）

- [x] 3.4.1 提交 `V015__extend_agent_run_step_confirmation_states.sql` 草案，说明 `CONFIRM_ACTION`、`WAITING_CONFIRMATION` 时间规则、兼容方案和无数据回填结论；验证：A 静态审查通过，V015 已合入 `origin/dev`。（B / A）

## 4. 集成与验证（B / C）

- [x] 4.1 使用确定性 `ModelGateway` 测试替身完成多工具和重规划验证；验证：`MultiToolSupervisorTest` 与状态机测试通过。
- [x] 4.2 运行 GitHub Actions `Backend MySQL Integration` 的 `mysql-integration`，记录运行编号、空库/重复初始化、CAS、重规划版本、事件顺序和结果未知恢复结果；验证：GitHub Actions #162 通过。（B）
- [x] 4.3 完成 C 卡片 payload 合入后的安全字段接入；验证：`AgentPersistenceJsonFactoryTest` 与事件相关测试通过。 （B / C）
- [x] 4.4 运行 `backend/mvnw.cmd verify`、严格 OpenSpec 校验、`git diff --check` 和变更文件核对；验证：502 项测试通过、27 项按环境跳过，Checkstyle 与 SpotBugs 通过。
