## 1. 模型计划与回复类型

- [x] 1.1 扩展 `PlanGenerationRequest`，加入不可变的服务端确认槽位；保持 `clientRequestId`、当前文本和允许工具列表校验。验证：构造、不可变和非法输入单元测试。（Owner：B）
- [x] 1.2 新增 B 自有的结构化回复类型、消息类型和推荐回复事实，字段只覆盖可展示的已校验结果，不使用 `Map<String,Object>`。验证：字段、不可变和敏感字段反射测试。（Owner：B）
- [x] 1.3 扩展 `ReplyGenerationRequest`/`ReplyGenerationResponse`，承载输出类型和类型化回复事实，并保持相同输入结果可复现。验证：正常、缺参和确定性单元测试。（Owner：B）

## 2. 最小计划生成和槽位判断

- [x] 2.1 更新 `MockModelGateway.generatePlan`：完整 `movieId`、`cinemaId`、`date` 生成 `rankMoviePlan → RENDER_RESULT`，可选时间仅成对引用。验证：完整槽位及有/无时间范围测试。（Owner：B）
- [x] 2.2 缺少工具必填槽位时生成单个 `ASK_USER`，只询问白名单顺序中的第一项缺失字段；工具未获允许时不生成工具节点。验证：逐项缺失和白名单拒绝测试。（Owner：B）
- [x] 2.3 更新 `MockModelGateway.generateReply`，根据输出类型和已校验回复事实生成确定性的 `QUESTION`、`PLAN_CARD`、`MOVIE_CARD`、`PROGRESS` 或 `ERROR`。验证：五类回复和相同输入重复调用测试。（Owner：B）

## 3. 最小只读主控流程

- [x] 3.1 新增最小只读 Agent 请求和结果类型，承载当前请求、服务端 `PlanValidationContext`、可信运行元数据、校验结果、可空运行快照、工具结果和结构化回复。验证：构造与非法元数据测试。（Owner：B）
- [x] 3.2 新增 Application 层最小主控服务，从 `ToolRegistry` 生成允许列表并调用 `ModelGateway.generatePlan`，随后使用当前服务端上下文重新执行 `PlanSchemaValidator`。验证：模型返回非法计划时零状态初始化、零 D 调用测试。（Owner：B）
- [x] 3.3 校验成功后初始化状态机，按计划顺序自动执行当前及新满足依赖的 `rankMoviePlan` 节点；调用方不得传 `CandidatePlan` 或 `nodeId`。验证：单节点、依赖节点、独立分支和调用顺序测试。（Owner：B）
- [x] 3.4 在主控内处理 `ASK_USER` 和 `RENDER_RESULT` 非工具节点，通过状态机记录开始/成功并返回对应回复；其他非工具、未知或写节点安全停止。验证：追问、渲染和范围外节点测试。（Owner：B）
- [x] 3.5 实现有限循环和处理中处理：最多 `计划节点数 × 2` 次节点处理，`PROCESSING` 保持 `RUNNING` 并立即返回 `PROGRESS`，同一轮不重复调用。验证：首次重试、第二次失败、处理中和循环上限测试。（Owner：B）
- [x] 3.6 在 Agent 配置中显式装配 `PlanSchemaValidator`、开发/测试用 `ModelGateway` 和最小主控服务，复用同一个 `ToolRegistry`；不得通过 Bean 名或字符串选择工具。验证：Spring 上下文和依赖唯一性测试。（Owner：B）

## 4. 回复事实映射与失败语义

- [x] 4.1 把 `FixedRecommendationResult` 映射为 B 自有回复事实，保留候选引用、可购性、来源、时间、缺失因素和降级标记，不传原始工具响应。验证：字段映射和敏感字段排除测试。（Owner：B；D 不改代码）
- [x] 4.2 正常可购结果生成 `PLAN_CARD`，无场次成功降级生成 `MOVIE_CARD` 且不得伪造 `showId`、价格或开场时间。验证：正常推荐和 `SHOWTIME` 降级测试。（Owner：B）
- [x] 4.3 参数值非法沿用 `FAILED + 100001`，可重试失败按状态机最多重试一次，最终失败返回只含稳定错误码和安全文案的 `ERROR`。验证：参数错误、首次失败成功、连续失败和独立分支测试。（Owner：B）

## 5. 请求到回复联合测试与交付

- [x] 5.1 使用 D 的真实 `RankMoviePlanTool` 固定夹具，覆盖“完整请求 → Mock 计划 → 服务端校验 → 自动推荐 → 结构化回复”的最小 B-D 联合流程。验证：联合测试通过且只调用公开 Java API。（Owner：B；D 不改代码）
- [x] 5.2 覆盖缺槽位、非法候选计划、无场次、参数错误、处理中和最终失败，确认 D 调用次数、节点状态和回复类型正确。验证：主控边界测试通过。（Owner：B）
- [x] 5.3 更新既有 Mock、计划校验、状态机和推荐适配测试，确保新增请求/回复字段不破坏已有行为。验证：受影响 Agent/推荐定向测试全部通过。（Owner：B）
- [x] 5.4 执行 `backend/mvnw.cmd verify`，记录测试通过、失败、跳过数以及 Checkstyle、SpotBugs、JaCoCo 结果。验证：后端完整校验通过。（Owner：B）
- [x] 5.5 执行 `openspec validate agent-readonly-plan-executor --strict`、`git diff --check` 和范围核对；记录 D 已确认接口未变，A、C 无新增确认项。验证：严格校验通过且仅勾选已完成任务。（Owner：B）
