# CineWise 开发协作规则

本文件约束在本仓库内工作的开发者和 AI。目标是先确认需求、接口和职责，再编码，避免跨模块改动、接口不一致和数据库迁移冲突。

## 1. 开始任何实现前

除纯文案或格式修正外，开始前必须按顺序完成以下检查：

1. 阅读仓库根目录 `README.md`，以及与本次改动对应的 `docs/` 规则文档。后端先读 `docs/backend-skeleton.md`，前端先读 `docs/frontend-coding-standards.md`。
2. 执行 `openspec list`，确认是否已有覆盖本次工作的 change；有则继续该 change，没有则先创建新的 change。
3. 阅读本次 change 下的 `proposal.md`、`specs/**/spec.md`、`design.md` 和 `tasks.md`。缺少能说明需求、验收、技术方案或任务拆分的文件时，先补文件，不得直接写业务代码。
4. 阅读与改动模块对应的详细设计和系分。后端和前端的业务设计文档位于相邻目录 `../CineWise-Docs/`，见第 2 节。
5. 确认影响范围。接口、DTO、事件、状态、错误码、数据库表、迁移、权限或跨模块调用有变化时，必须先获得受影响模块 Owner 的确认，并在 OpenSpec 中记录。

不允许以“先写出来再说”为由绕过上述步骤。需求或职责有冲突时，停止实现，先更新 OpenSpec 和相关设计文档。

## 2. 必读文档与选择规则

所有代码改动必须阅读仓库内 `docs/` 对应的编码规范。代码规范以 `CineWise/docs/` 为准，不需要从 `CineWise-Docs/` 查找：

- 后端：`docs/backend-coding-standards.md`、`docs/backend-skeleton.md`
- 前端：`docs/frontend-coding-standards.md`
- Git：`docs/GIT_COMMIT_CONVENTION.md`
- 数据库迁移：涉及表结构、Flyway 或种子时阅读 `docs/DATABASE_MIGRATION_REVIEW.md`

后端业务改动还必须阅读：

- `../CineWise-Docs/系分文档/后端/妙语购票_后端系统分析设计.md`

按模块额外阅读：

| 改动范围 | 额外必读文档 | Owner |
| --- | --- | --- |
| `agent/**`、Agent Tool、SSE、确认动作 | `../CineWise-Docs/系分文档/后端/妙语购票_Agent智能决策中心系统分析设计.md` | B |
| `auth/**`、JWT、权限、验证码、邮件公共端口 | `../CineWise-Docs/系分文档/后端/用户认证设计.md` | C |
| `ticketing/**`、`order/**`、支付、退票 | `../CineWise-Docs/系分文档/后端/CineWise-A-票务交易全栈系统分析设计.md` | A |
| `profile/**`、`content/**`、`recommendation/**`、`travel/**` | `../CineWise-Docs/系分文档/后端/数据与专业Agent/` 中对应设计 | D |
| 公共接口、跨模块流程或职责判断 | `../CineWise-Docs/系分文档/妙语购票_总体系统分析设计.md` | 全组 |

前端改动还必须阅读：

- `../CineWise-Docs/系分文档/前端/妙语购票_前端系统分析设计.md`
- `../CineWise-Docs/系分文档/前端/前端应用系统设计.md`

前端页面、组件和接口实现不能只依据截图或个人习惯。原型图只说明页面层级和交互方向，不能替代接口、权限、状态和异常规则。

涉及 Spring Boot、MyBatis-Plus、Spring Security、Spring AI、Umi 或任何第三方库、SDK、CLI、云服务的配置、API 用法或版本升级时，先查当前官方文档或 Context7，再实现；不得凭记忆使用旧 API。

## 3. 模块边界

| 模块 | Owner | 可负责的范围 |
| --- | --- | --- |
| `common`、`ticketing`、`order`、`admin`、`job` | A | 公共基础、票务交易、订单支付退票、管理、调度和部署 |
| `agent` | B | Agent 会话、计划、运行、工具协议、确认、SSE 和轨迹 |
| `auth` | C | 登录认证、JWT Cookie、SecurityContext、验证码和邮件公共端口 |
| `profile`、`content`、`recommendation`、`travel` | D | 画像、内容、推荐、出行与提醒 |

前端改动按以下范围确认负责人：

| 前端范围 | 主要负责人 | 协作边界 |
| --- | --- | --- |
| `app`、布局、路由守卫、公共请求层、主题、Agent 工作区和管理工作台 | C | 复用后端公开 API；B 提供 Agent 协议，A/D 提供业务 DTO |
| 场次、选座、订单确认、支付、电子票、退票和用户订单 | A | 复用 C 的公共壳层、请求封装和组件规范；业务状态以 A 后端接口为准 |
| 影片、影院、画像、推荐、出行页面 | 按当前 OpenSpec 和前端设计确认 | 不得擅自把 D 的业务规则写入公共组件或另建请求封装 |
| `shared`、公共 DTO、SSE 客户端、错误映射 | C 维护 | 影响 A/B/D 时必须由受影响 Owner 审查 |

必须遵守：

- 每个业务模块按 `api/application/domain/infrastructure` 分层。
- Controller、SSE 适配器、Job、事件监听器和 Agent Tool Adapter 只能调用 Application Service。
- 禁止跨模块访问 Entity、Mapper、Repository 或持久化包；禁止通过本机 HTTP 调用本应用 Controller。
- 当前用户只能经 `CurrentUserAccessor` 获取；业务请求、Agent 参数和前端请求体中的 `userId`、角色、金额、订单状态均不可直接信任。
- Agent 不得直接读写票务、订单、支付、退票表，只能调用 A 提供的类型化 Application API/Tool。
- 新增或修改 Flyway 脚本前，Owner 必须在 OpenSpec 确认字段、索引、生命周期和兼容方案；A 统一分配版本号。不得修改已共享或已执行的迁移。

前端还必须遵守：

- `pages` 只负责路由参数读取、页面组合和流程编排，不直接发送网络请求；请求必须经过模块 API、Hook 和公共 Umi `request` 封装。
- `modules` 只管理自己的查询、写操作和状态规则；不得读取其他模块的私有 store 或缓存。
- `features` 只承载可复用的复杂业务组件；不得复制模块接口调用和状态恢复逻辑。
- 普通 REST 使用统一请求层，Agent 流使用公共 SSE 客户端；不得另建请求封装。
- 前端业务 ID、SSE `eventId` 使用 `string`；金额使用两位小数字符串；时间使用 ISO 8601；不得把 ID 转成 JavaScript `number`。
- 前端不读取、保存、打印 Cookie、JWT、验证码、支付密码或其他密钥；401、403、404、409、422 和 5xx 按错误码和状态处理。
- 建单、支付、退票、Agent 确认等写操作遇到超时或断网时只查询原结果，不自动生成新幂等键重试。

## 4. OpenSpec 标准流程

一个 change 只覆盖一组可独立验收的功能，不把无关重构混入其中。流程固定如下：

1. **提出变更**：创建名称明确的 `openspec/changes/<change-name>/`，写 `proposal.md`，明确背景、范围、非范围、Owner 和验收结果。
2. **定接口和规则**：在 `specs/**/spec.md` 写可验证的需求和场景，尤其是权限、状态、幂等、异常、SSE、事件与兼容性。
3. **定实现方案**：在 `design.md` 写分层、数据、状态、依赖、迁移、恢复和测试方案。
4. **拆可执行任务**：在 `tasks.md` 按编码、测试、文档、联调拆分。任务必须能独立验证并标明 Owner。
5. **评审后实现**：受影响 Owner 确认契约后再编码。实现过程仅勾选已完成且已验证的任务。
6. **校验和合并**：执行 `openspec validate <change-name> --strict`、第 5 节的构建和相关联调；PR 中说明结果。
7. **归档**：所有任务、测试和消费者同步完成后，更新最终规范并归档 change。未完成的 change 不得归档。

使用 AI 助手时，可用 `/opsx:propose` 创建提案、`/opsx:apply` 按任务实现、`/opsx:archive` 完成后归档；它们是助手指令，不是 PowerShell 命令。命令行用 `openspec list`、`openspec status --change <change-name>` 和 `openspec validate` 查看、校验变更。

## 5. 编码、测试和本地环境

- 后端统一使用 Java 21、Spring Boot 3.5、Maven Wrapper。提交前在 `backend/` 执行 `./mvnw.cmd verify`；必须通过编译、测试、ArchUnit、Checkstyle、SpotBugs 和 JaCoCo。
- 前端提交前按 `frontend/package.json` 中已有脚本执行安装、类型检查、Lint、单元测试和构建；如果前端工程尚未初始化，先由 C 按 OpenSpec 完成工程骨架和脚本，不得自行引入第二套构建入口。
- 前端改动必须覆盖对应的加载、空数据、失败、无权限、重复提交、刷新恢复和响应式场景；Agent/SSE 改动还要覆盖重复事件、断线恢复、旧计划事件和未知事件降级。
- 改动数据库、Redis、Docker、迁移、并发、SSE 或外部适配器时，除单元测试外，还要按 OpenSpec 在真实云端 MySQL、云端 MinIO（启用对象存储时）和 Docker Redis 环境验证。先执行 `docker compose config --quiet`，再按需运行 `docker compose up -d redis`；迁移首次验证只可连接独立的 `cinewise_migration_check` 库。
- 不提交 `.env`、真实密钥、容器数据、构建产物、日志、调试代码或无归属 TODO。
- 业务 ID 对外为十进制字符串；金额在 Java 内使用 `BigDecimal`，对外为两位小数字符串；业务时间通过注入的 `Clock` 获取。
- API、错误码、OpenAPI、Mock、消费者夹具和测试必须随契约变更一起更新。

## 6. Git 与代码评审流程

1. 开工前同步开发基线：`git switch dev`、`git pull --ff-only origin dev`。不要直接在 `dev` 开发，应从 `dev` 创建本次 OpenSpec change 对应的短生命周期分支。
2. 每个 OpenSpec change 使用一个短生命周期分支，例如 `feat/agent-runtime-and-workspace`、`fix/auth-cookie-expiry`。
3. 每次提交只包含一个目的，提交前检查 `git diff --check`、`git status` 和变更文件。提交信息使用 `feat(agent): ...`、`fix(auth): ...`、`test(ticketing): ...`、`docs(openspec): ...` 等格式。
4. 禁止使用 `git reset --hard`、强推、覆盖他人提交或提交无关格式化；需要整理历史或处理冲突时先确认影响范围。
5. PR 必须说明：关联 OpenSpec change、修改范围、契约影响及已确认 Owner、验证命令和结果、未验证事项与风险。跨模块 PR 必须由受影响 Owner 审查。
6. 数据库迁移是唯一的直提例外：只有 A 可以在 Owner/OpenSpec 确认、版本分配、静态审查和空 MySQL 8.4 验证完成后，将隔离的 Flyway 迁移及直接关联证据提交到 `dev`；其他业务代码、测试和文档仍须通过个人分支 PR。
7. 合并前工作区必须干净，CI 和本地验证通过；合并后检查 OpenSpec 任务状态，不把未完成任务标为完成。

## 7. 冲突处理

发现 PRD、总体设计、详细设计、OpenSpec、代码或测试之间有冲突时，优先级为：安全和法律要求 > 已确认的 PRD/系统设计/跨模块契约 > OpenSpec > 本文件 > 个人实现习惯。

冲突出现时，记录具体文件、字段或接口及受影响模块，通知 Owner；在结论明确前不通过临时代码、兼容分支或跨模块访问绕过问题。
