# CineWise 数据库迁移审查与验证规范

本文用于审查和验证 CineWise 的 Flyway SQL 迁移。目标是在迁移进入任何共享数据库前，先发现版本冲突、字段遗漏、跨模块越权与 MySQL 兼容问题。

## 1. 适用范围与责任

Flyway 文件统一位于 `backend/src/main/resources/db/migration/`。每一份新增迁移都必须有对应 OpenSpec change，并遵循“先审查、后执行”的顺序。

| 角色 | 责任 |
| --- | --- |
| 领域 Owner | 在 OpenSpec 中确认本领域表、字段、索引、约束、生命周期与兼容方案；提出迁移需求并审查业务语义。 |
| A | 分配 Flyway 版本号，核对全局规则，审核或生成最终 SQL，决定是否允许执行，在空 MySQL 中执行验证并保存证据。 |
| AI 审查助手 | 只读分析 OpenSpec 与 SQL，输出问题和验证建议；不得连接数据库、执行 SQL、修改文件或批准迁移。 |

其他成员不得自行占用迁移版本号，不得自行开启 `FLYWAY_ENABLED=true`，也不得对共享数据库手工执行迁移 SQL。

## 2. 迁移流程

```text
OpenSpec 确认
  → Owner 确认字段与业务规则
  → A 分配版本并完成静态审查
  → AI 只读复核
  → A 授权
  → 全新空 MySQL 8 执行 Flyway
  → 表、索引、约束、重复执行验证
  → 在 OpenSpec tasks 或 PR 中记录证据
```

“33 张表完整基线验证”是所有模块表完成后的总验收；单个 feature 的迁移不必等待全部表确定。每个 feature 在自身字段确认后，就可以由 A 授权进入空 MySQL 验证。

## 3. 成员提交给 A 的材料

申请迁移审查时，领域 Owner 应一次提供以下内容：

```text
OpenSpec change：openspec/changes/<change-id>/
领域 Owner：<姓名或角色>
涉及表：<表名列表>
申请版本：<由 A 分配后填写，例如 V004>
迁移文件：backend/src/main/resources/db/migration/<文件名>
新增或修改的索引、唯一约束、CHECK 约束：<列表>
需要验证的业务场景：<列表>
```

缺少 OpenSpec、字段 Owner 确认或 A 分配版本号时，不进入 SQL 执行阶段。

## 4. AI 审查提示词

将下列提示词与 OpenSpec、待审 SQL、已有迁移文件一起提供给 AI。AI 的结论仅供 A 审查，不构成执行授权。

```text
你是 CineWise 数据库迁移审查助手。你只能读取和分析文件，
不得执行 SQL、连接数据库、修改文件、输出或请求任何密码、密钥、JWT。

输入：
1. 对应 OpenSpec change：<路径>
2. 待审迁移文件：<路径>
3. 已有迁移目录：backend/src/main/resources/db/migration
4. 项目规则：docs/backend-skeleton.md
5. 本规范：docs/DATABASE_MIGRATION_REVIEW.md

请检查：
- Flyway 文件名、版本号、描述是否规范，是否与已有版本冲突；
- SQL 是否包含 DROP、TRUNCATE、无条件 DELETE、REPLACE 或其他危险语句；
- 是否修改、删除或重命名历史迁移；
- 是否错误建立跨模块物理外键，或通过数据库约束越过模块边界；
- BIGINT 雪花 ID、DATETIME(3)、DECIMAL(10,2)、utf8mb4 等规则是否遵守；
- 必要的唯一约束、索引、CHECK、version 与审计字段是否缺失；
- SQL 是否与 OpenSpec 中的字段、状态、Owner 边界和生命周期冲突；
- 是否混入非结构性演示种子、真实账号、密码、JWT 或其他密钥；
- A 在空 MySQL 中还需要验证哪些项目。

输出格式：
1. 结论：通过 / 需修改 / 拒绝执行；
2. 问题清单：按严重程度排序，标明文件、行号和原因；
3. 最小修改建议；
4. 空 MySQL 验证清单；
5. 明确说明：本次审查未执行 SQL，最终执行权属于 A。
```

## 5. A 的静态审查清单

```text
[ ] 对应 OpenSpec 的 proposal、spec、design、tasks 已完整且字段 Owner 已确认
[ ] 迁移名称符合 V<版本>__<英文描述>.sql，版本由 A 分配且没有重复
[ ] 历史迁移没有被修改、改名或删除
[ ] SQL 没有 DROP、TRUNCATE、无条件 DELETE、REPLACE 等危险语句
[ ] 内部主键为 BIGINT 雪花 ID；时间为 DATETIME(3)；金额为 DECIMAL(10,2)
[ ] 字符集、排序规则、可空性、默认值与 OpenSpec 一致
[ ] 必要的 UNIQUE、普通索引、CHECK、version 和审计字段已定义
[ ] 跨模块只保存业务 ID，不建立物理外键；不通过其他模块 Mapper 写表
[ ] 迁移不混入真实数据、密码、JWT、账号或不可重复的演示种子
[ ] 待验证的并发、幂等、状态与重复执行场景已列出
```

发现问题时，A 应退回 SQL 草案并要求 Owner 更新 OpenSpec 或 SQL；不得为了“先跑起来”跳过确认门。

## 6. 空 MySQL 验证规则

SQL 静态审查不能替代真实 MySQL 验证。语法、索引长度、字符集、CHECK、默认值和 MySQL 8 行为，必须由真实 MySQL 8 执行结果确认。

验证环境使用本机 Docker 的 MySQL 8.4 容器即可，但必须满足：

- 使用独立、可删除的空数据库和空数据卷；不得使用日常联调的 `cinewise` 数据库。
- 不使用 Windows 本机 MySQL 服务或任何云数据库作为首次验证环境。
- 数据库密码只保存在被 Git 忽略的 `.env` 中；不得写入本文、SQL、OpenSpec、日志或提交记录。
- `FLYWAY_ENABLED` 默认保持 `false`；只在 A 进行本次受控验证时临时启用，验证后恢复默认值。

### 环境文件隔离

日常开发阶段只维护现有 `.env`，它只服务于本机 Docker 联调，且 `FLYWAY_ENABLED` 必须保持 `false`。不得为了迁移审查修改其中已有的 MySQL 密码、数据库名或数据卷。

只有在 A 已完成静态审查并明确授权进行迁移验证时，才创建被 Git 忽略的 `.env.migration-check`。该文件只指向独立、可删除的 Docker 空库；验证完成后可连同该验证库的数据卷删除。

未来如接入云数据库，云凭据只可放在独立、被 Git 忽略的 `.env.cloud` 中。`.env.cloud` 不得作为 `docker compose up` 的默认环境文件，也不得用于首次迁移验证。

```text
.env                     日常本机 Docker 联调；默认不执行迁移
.env.migration-check     仅在 A 授权的空库迁移验证时创建和使用
.env.cloud               未来云环境凭据；不参与首次迁移验证
```

每次验证至少记录：

```text
[ ] 使用的 MySQL 版本和空数据库标识
[ ] 执行前 Flyway info 结果
[ ] migrate 成功结果与 flyway_schema_history 记录
[ ] 表、列、主键、UNIQUE、普通索引、CHECK 的实际检查结果
[ ] 应用或 Flyway 再执行一次后的结果：不得重复建表或改写历史版本
[ ] 失败时的错误摘要、处理结论与是否需要回退 SQL 草案
```

真实 MySQL 验证通过后，才可以在对应 OpenSpec 的任务中勾选“空 MySQL 执行迁移”。

## 7. 禁止事项

- 不把 `MYSQL_ROOT_PASSWORD`、业务账号密码、JWT、SSH 私钥或云数据库连接串提交到 Git。
- 不把 AI 的“通过”当作执行授权。
- 不在共享库、云库或已有联调数据的数据库上做首次迁移验证。
- 不修改已经共享或执行的 Flyway 历史迁移；需要调整时新增一个向前迁移。
- 不把 Flyway 结构迁移当作演示种子、账号初始化或业务状态修复工具。

## 8. 验证记录模板

将下列模板放入对应 OpenSpec 的 `tasks.md`、PR 描述或团队约定的验证记录中：

```text
迁移：V<版本>__<描述>.sql
OpenSpec：<change 路径>
Owner 确认：<人员与日期>
A 授权：<日期>
AI 审查结论：通过 / 需修改，问题已处理：<是/否>
MySQL：8.4.x，本机 Docker 空库
migrate：通过 / 失败
validate：通过 / 失败
重复执行：通过 / 失败
结构检查：通过 / 失败
未验证项与原因：<内容>
记录人：A
```
