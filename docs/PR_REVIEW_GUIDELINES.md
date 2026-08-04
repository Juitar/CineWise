# CineWise PR 审查规范

审查当前 PR 时遵守以下规则。重点发现可验证的运行、安全、契约和协作风险，不因个人格式偏好制造无效评论。

本文件是团队和自动审查工具共同使用的正式规范；`.github/copilot-instructions.md` 仅作为 GitHub Copilot 的自动发现入口。

## 分支、范围与 Owner

- `dev` 是日常集成分支。普通功能、修复、重构和测试从最新 `dev` 创建个人短生命周期分支，并通过 PR 合回 `dev`；`AGENTS.md` 明确列出且不改变运行行为的小型文档、协作规则和 CI 触发范围维护可以直接提交 `dev`，Flyway 迁移仅适用 A 的隔离直提例外。除这些例外外，不得把普通开发提交直接推到 `dev` 或 `main`。
- 分支应与一个 OpenSpec change 或一个清晰修复目的对应，例如 `feat/password-login-and-session`、`fix/auth-cookie-expiry`、`docs/database-review`。
- 审查前先确认 PR 的 OpenSpec、主要模块、Owner 和受影响消费者。公共 API、DTO、事件、Agent Tool、权限、状态、数据库结构或前端公共请求层发生变化时，必须有受影响 Owner 的确认。
- 不要求提交人顺手修复其他 Owner 的并行工作。对范围外问题只记录依赖、影响和负责人；仅当本 PR 破坏已有契约、越过模块边界或无法独立运行时提出 Request changes。
- PR 只包含当前目的所需文件，不得混入其他个人分支、未完成迁移、本地配置、生成产物或无关格式化。

## OpenSpec 与公共契约

- 非纯文案改动必须先确认对应 `openspec/changes/<change-name>/`。需求、验收、方案或任务不完整时，不应先合并业务实现。
- PR 实现必须与 `proposal.md`、`specs/**/spec.md`、`design.md` 和 `tasks.md` 一致；只勾选已经完成并有验证证据的任务。
- REST 路径、请求/响应字段、错误码、Cookie/CSRF、SSE 事件、工具 Schema、状态和权限都是公共契约。变更时同步 OpenAPI/Schema、消费者夹具、测试和文档。
- 业务 ID 对外使用十进制字符串，金额对外使用两位小数字符串，时间使用已冻结的 ISO 8601 语义；不得让 JavaScript `number` 承载雪花 ID。

## 架构与模块边界

- 后端是模块化单体，各业务模块按 `api/application/domain/infrastructure` 分层。Controller、Job、事件监听器、SSE 适配器和 Agent Tool Adapter 只能调用 Application Service。
- 禁止跨模块访问 Entity、Mapper、Repository 或持久化包，禁止通过本机 HTTP 调用本应用 Controller。跨模块协作只能使用公开 Application API、DTO、事件或类型化 Tool。
- 当前身份只能从 C 提供的 `CurrentUserAccessor` 或可信安全上下文取得；不得信任请求体中的 `userId`、角色、金额、订单状态或资源归属。
- Entity/持久化模型不得直接作为 HTTP、SSE 或 Agent Tool 的请求响应模型。
- A 负责 `common/ticketing/order/admin/job`，B 负责 `agent`，C 负责 `auth` 及公共前端壳层，D 负责 `profile/content/recommendation/travel`；跨 Owner 改动必须由受影响方审查。

## 有效注释率门禁（必须）

- 只要 PR 新增或实质修改 `backend/src/main/java` 中的手写生产代码，就必须审查有效注释率；纯文档、测试、配置、迁移和生成代码 PR 标记为“不适用”，不得用这些文件冲抵生产代码注释率。
- PR 注释率按目标分支到当前 PR 头提交的新增行计算；实质修改在差异中表现为新增行，同样纳入统计。计算公式为“有效注释行数 ÷（有效注释行数 + 有效代码行数）”，结果不得低于 30%，不通过四舍五入把低于 30% 的结果判为合格。
- 统计排除空行、`package`、`import`、仅含括号或分隔符的行以及生成代码。Javadoc、块注释和行注释只有在说明业务原因、约束、状态、并发、安全、兼容或失败后果时才计入；许可证头、重复模板、逐行翻译语法、被注释掉的废弃代码和为凑比例添加的无意义注释不计入。
- 除 PR 差异达到 30% 外，新增或修改代码不得降低所属模块的有效注释率，合并后的后端手写生产代码整体仍须达到 `docs/backend-coding-standards.md` 规定的 30% 门槛。不得通过修改无关文件或集中添加与本次改动无关的注释补足比例。
- PR 描述必须给出可复现的统计命令或工具、有效注释行数、有效代码行数和最终百分比；审查者需要抽查被计入的注释是否有效。未提供证据、统计口径错误、实际低于 30% 或使用无意义注释凑比例时，必须提交 `Request changes`，不能仅作为非阻断建议。

## 数据库迁移直提例外

- `backend/src/main/resources/db/migration/V*.sql` 不走普通功能 PR。只有 A 可以在对应 Owner 和 OpenSpec 已确认后，分配 Flyway 版本、审核或生成最终 SQL，并以隔离提交直接提交到 `dev`。
- A 直提 `dev` 前必须完成 `docs/DATABASE_MIGRATION_REVIEW.md` 规定的静态审查、AI 只读复核、专用空 MySQL 8.4 首次迁移、重复迁移、结构/索引/CHECK/排序规则验证，并保存证据。
- 直提提交只包含已审核迁移以及直接关联的迁移验证记录或 OpenSpec 证据，不得夹带 Java、前端业务实现或其他人的改动。业务代码仍从个人分支通过 PR 合入 `dev`。
- 迁移与应用代码分开进入 `dev` 时，A 必须确认迁移向后兼容并明确合入顺序。破坏性或强耦合迁移必须先形成联合发布和恢复方案，不得仅凭“由 A 直提”绕过兼容性审查。
- 已经共享或执行的迁移是不可变历史，不得修改、删除或重命名。后续结构调整使用 A 分配的更高版本向前迁移。
- 其他成员的 PR 不应自行占用版本号或提交最终迁移 SQL。发现数据库需求时，应要求其补齐 OpenSpec、字段、索引、约束、生命周期和验证场景，并交由 A 处理迁移提交。
- 不在迁移中写入真实账号、密码、JWT、连接信息或非结构性演示种子；跨模块只保存业务 ID，不建立物理外键。

## 高优先级审查项

1. 鉴权、CSRF、当前用户归属、ADMIN 权限、数据范围和敏感字段脱敏。
2. 订单、锁座、支付、电子票、退票和 Agent 确认的幂等键、数据库最终约束、事务边界、并发竞争和结果恢复。
3. 写请求超时、断网或 SSE 重连后不得自动生成新幂等键重试；只能使用原标识查询权威结果。
4. 状态迁移必须校验当前状态和受影响行数，终态不得被普通更新重新打开。
5. Entity、Mapper、SQL 与实际 MySQL 列名、类型、可空性、索引和排序规则保持一致。
6. SSE/异步流程覆盖成功、失败、取消、超时、重复事件、断线恢复和未知事件降级。
7. 公开 DTO、错误码、权限、路由、公共请求层和跨模块契约不得被意外破坏。
8. 按“有效注释率门禁”核验 PR 差异、所属模块和后端整体均满足 30% 要求；缺少可复现证据或不达标时必须 `Request changes`。

## 自动修复与评论规则

- 仅自动修复当前 PR 范围内、不改变数据库、公开 API、权限、配置或跨模块契约的问题，并补充能够证明缺陷的回归测试。
- 不自动修改 Flyway 历史迁移、OpenSpec 决策、依赖版本、运行时配置、密钥、公共 DTO 或权限策略；对此类问题给出触发条件、实际影响和最小修复方向。
- 评论应区分阻断问题、非阻断风险和建议。没有可验证影响的格式偏好不应报告。
- 若依赖模块尚未完成，但本 PR 已正确通过公开端口隔离依赖，应记录为联调前置条件，不应仅因此 Request changes。

## 验证与合并

- 后端改动在 `backend/` 执行 `./mvnw.cmd verify`，并如实报告测试、Checkstyle、SpotBugs、ArchUnit 和覆盖率结果。
- 前端改动在 `frontend/` 执行 `pnpm check`；涉及关键跨页流程时补充相应 E2E。不得把未安装环境或未执行命令写成“通过”。
- OpenSpec 执行 `openspec validate <change-name> --strict`。数据库、Redis、SSE、并发或外部适配器还应完成对应真实环境或集成验证。
- PR 描述必须列出关联 OpenSpec、改动范围、契约/数据库/权限/配置影响、已确认 Owner、实际验证命令与结果、未验证事项和风险。
- 提交信息遵守 `docs/GIT_COMMIT_CONVENTION.md`，使用英文 Conventional Commit 协议词和中文主题，例如 `feat(order): 增加订单超时关闭`。
- 不提交 `.env`、Token、数据库连接串、Cookie、密码、密钥、生产数据、日志、`target`、`node_modules` 或本地容器数据。
