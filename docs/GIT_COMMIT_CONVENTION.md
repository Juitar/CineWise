# CineWise Git 提交规范

本规范适用于 CineWise 仓库的人工提交和经成员明确授权的 AI Agent 提交。目标是让多人并行开发时的提交历史可读、可审查、可回退，并让 OpenSpec、迁移、代码与测试之间保持可追溯。

## 1. 基本原则

- 一个提交只完成一个清晰目的，应能独立理解、审查和回退。
- 提交说明必须说明实际变更；禁止使用 `update`、`修改一下`、`临时提交`、`WIP`、`fix bug` 等没有具体含义的描述。
- 提交前只暂存当前目的需要的文件；不得混入本地配置、构建产物、密钥、日志或无关格式化。
- `type`、`scope` 和约定 footer 是固定英文协议词；`subject` 与正文应优先使用中文，使团队能直接读懂提交历史。
- 不使用 `git commit --no-verify` 绕过提交校验；校验失败应修正提交说明或代码。
- AI Agent 未经用户明确授权不得自行创建提交、amend、rebase、force push 或推送。

## 2. Commit message 格式

普通提交格式：

```text
<type>(<scope>): <subject>
```

当影响范围不明确时，可以省略 `scope`：

```text
<type>: <subject>
```

规则：

- `type` 必填，使用小写英文。
- `scope` 选填，使用小写英文，表示主要受影响模块或工程范围。
- 冒号必须是英文半角 `:`，后面必须有一个空格。
- `subject` 必填，使用简洁中文描述动作和对象；产品名、技术名和代码标识可保留英文，不以句号结尾，建议不超过 72 个字符。
- 简单提交只写标题；涉及迁移、兼容、部署、安全或跨模块契约时，应增加正文说明影响与验证方式。

固定英文协议词不可翻译。例如应写：

```text
build(deploy): 更新 Docker Compose 健康检查
feat(ticketing): 新增场次座位查询
```

不得写：

```text
构建(deploy): 更新 Docker Compose 健康检查
新增(ticketing): 场次座位查询
```

完整格式：

```text
<type>(<scope>): <subject>

[body]

[footer]
```

## 3. Type 类型

| Type | 使用场景 | CineWise 示例 |
| --- | --- | --- |
| `feat` | 新增业务能力或用户可见能力 | `feat(ticketing): 新增场次座位查询` |
| `fix` | 修复错误行为 | `fix(order): 修复重复支付请求的幂等返回` |
| `docs` | 仅修改文档、OpenSpec 或注释 | `docs(openspec): 明确订单创建确认边界` |
| `style` | 仅格式调整，不改变行为 | `style(backend): 统一 Java 导入顺序` |
| `refactor` | 不新增能力、不修复缺陷的代码重构 | `refactor(agent): 收敛工具参数校验入口` |
| `perf` | 性能优化 | `perf(recommendation): 减少推荐候选集重复查询` |
| `test` | 独立新增或调整测试 | `test(ticketing): 补充并发锁座集成测试` |
| `chore` | 日常维护、辅助工具或非产品代码变更 | `chore(repo): 更新忽略文件规则` |
| `revert` | 回退已有提交 | `revert: 回退场次种子初始化改动` |
| `build` | 构建、依赖、CI 或部署编排变更 | `build(deploy): 调整 Compose 健康检查` |

功能提交同时包含必要测试时，使用 `feat` 或 `fix` 即可；只有独立补测时才使用 `test`。页面样式或交互实际变化时应使用 `feat` 或 `fix`，不能因为修改 CSS 就一律使用 `style`。

表中的 `feat`、`fix`、`build` 等是固定关键字，不能翻译为“新增”“修复”“构建”。它们让 commitlint、变更日志工具和团队成员能稳定识别提交类型；冒号后的中文才是给人阅读的具体说明。

## 4. Scope 范围

优先使用下列 scope，不使用 `frontend,backend` 等多 scope 拼接。

| 类别 | 推荐 Scope |
| --- | --- |
| 工程范围 | `repo`、`backend`、`frontend`、`api`、`database`、`deploy`、`docs`、`openspec`、`deps` |
| 后端模块 | `common`、`auth`、`profile`、`content`、`ticketing`、`order`、`agent`、`recommendation`、`travel`、`admin`、`job` |

选择规则：

- 使用最能代表主要目的的一个 scope。
- 同步改变前后端接口契约时使用 `api`；仅更新 OpenSpec 变更文件时使用 `openspec`。
- 迁移属于明确业务模块时使用该模块，如 `feat(ticketing)`；仅调整 Flyway 基础配置或迁移校验工具时使用 `database`。
- 新增 scope 前确认现有名称不能准确表达，避免同义词并存。

## 5. 迁移与 OpenSpec 提交规则

- 新增或修改公共 API、事件、工具、权限、状态或数据迁移前，先更新对应 `openspec/changes/<change-id>/`。
- 新增 Flyway 脚本前由 A 分配版本号；已经共享或执行的迁移不得改名、改内容或删除。
- 普通业务代码从个人分支通过 PR 合入 `dev`。Flyway 迁移是唯一的直提例外：由 A 在 Owner/OpenSpec 确认、静态审查和空 MySQL 8.4 验证完成后，以隔离提交直接提交到 `dev`。
- A 的迁移直提只能包含最终 SQL 及直接关联的验证记录或 OpenSpec 证据，不得夹带业务实现。迁移与实体、Mapper、应用服务和测试分开提交时，必须保证迁移向后兼容并明确可部署顺序；破坏性或强耦合迁移必须先形成联合发布和恢复方案。
- 不在迁移中混入非结构性演示种子、真实账号、密码、JWT 或其他密钥。
- 执行迁移前必须有对应 Owner 的字段确认与 A 的明确授权；执行证据应记录在该 OpenSpec change、迁移验证记录或后续业务 PR 描述中。

示例：

```text
feat(ticketing): 新增场次与座位结构迁移

新增 V002 迁移及空 MySQL 验证，跨模块关联仅保存业务 ID，不建立物理外键。
```

## 6. 正文、关联事项与破坏性变更

以下情形应增加正文：迁移、部署步骤、兼容策略、已知限制、模块间契约或安全边界变化。正文优先使用中文；`Refs` 与 `BREAKING CHANGE` 保持英文，以兼容 Conventional Commit 工具。

```text
fix(auth): 修复权限变更后旧会话仍然有效

权限变更成功后递增 tokenVersion，并清理会话缓存。

Refs: CW-128
```

不兼容变更必须在提交前完成团队确认，并显式标记：

```text
feat(api)!: 统一分页响应字段

BREAKING CHANGE: 列表响应由 items 调整为 records，前端调用方必须同步升级。
```

## 7. 提交前检查清单

```text
[ ] 一个提交只有一个主要目的
[ ] type、scope 和 subject 准确、具体且无句号
[ ] 暂存区没有 .env、密钥、target、node_modules、日志或本地数据
[ ] 公共变更已同步对应 OpenSpec、OpenAPI/Schema、消费者夹具和测试
[ ] 迁移版本由 A 分配，且未修改已经共享或执行的历史迁移
[ ] 已执行受影响模块需要的测试、构建和迁移校验
[ ] 已检查 git diff --cached，确认只有预期改动
```

后端提交至少执行：

```powershell
Set-Location backend
.\mvnw.cmd verify
```

## 8. 自动化校验计划

当前仓库未启用 commitlint、Husky 或 Commitizen，本规范先以人工检查执行。后续如启用自动校验，应在仓库根目录统一配置，使前端、后端、文档和迁移提交使用同一规则；不得只在前端目录安装 Hook。

推荐在独立 OpenSpec change 中完成：commitlint 配置、Husky `commit-msg` Hook、Windows 与 Unix Shell 验证。Commitizen 仅作为可选输入助手，不能替代 commitlint 校验。
