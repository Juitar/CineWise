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
- BIGINT 雪花 ID、DATETIME(3)、DECIMAL(10,2)、utf8mb4 和`utf8mb4_0900_ai_ci`等规则是否遵守；
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
[ ] 每张新表显式声明`ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci`
[ ] 数据库、表和字符串列的实际字符集、排序规则、可空性、默认值与OpenSpec一致
[ ] 必要的 UNIQUE、普通索引、CHECK、version 和审计字段已定义
[ ] 跨模块只保存业务 ID，不建立物理外键；不通过其他模块 Mapper 写表
[ ] 迁移不混入真实数据、密码、JWT、账号或不可重复的演示种子
[ ] 待验证的并发、幂等、状态与重复执行场景已列出
```

发现问题时，A 应退回 SQL 草案并要求 Owner 更新 OpenSpec 或 SQL；不得为了“先跑起来”跳过确认门。

## 6. 空 MySQL 验证规则

SQL 静态审查不能替代真实 MySQL 验证。语法、索引长度、字符集、CHECK、默认值和 MySQL 8 行为，必须由真实 MySQL 8 执行结果确认。

验证环境使用 A 专用的云端 MySQL 8.4 验证库 `cinewise_migration_check`，必须满足：

- 使用独立、可删除的空数据库；不得使用日常联调的 `cinewise` 数据库。
- 只使用 `cinewise_migrator` 账号连接 `cinewise_migration_check`；不得使用共享 `cinewise_app` 账号执行首次迁移验证。
- 数据库密码只保存在被 Git 忽略的 `.env` 中；不得写入本文、SQL、OpenSpec、日志或提交记录。
- `FLYWAY_ENABLED` 默认保持 `false`；只在 A 进行本次受控验证时临时启用，验证后恢复默认值。

### 环境文件隔离

日常开发阶段使用 `.env` 连接云端共享 `cinewise` 库，且 `FLYWAY_ENABLED` 必须保持 `false`。不得为了迁移审查修改其中已有的数据库名或启用 Flyway。

只有在 A 已完成静态审查并明确授权进行迁移验证时，才使用被 Git 忽略的 `.env.migration-check`。该文件只指向独立、可删除的云端 `cinewise_migration_check` 库。

`.env.cloud` 保留为云端共享库连接信息的参考文件；`.env.migration-check` 是 A 专用迁移验证配置，二者不得混用。

```text
.env                     云端共享 cinewise 库；默认不执行迁移
.env.migration-check     仅在 A 授权时连接云端 cinewise_migration_check 验证库
.env.cloud               云端共享库连接信息的参考文件
```

### 从验证库发布到共享 `cinewise` 库

专用 `cinewise_migration_check` 验证通过，不代表迁移已经进入共享 `cinewise` 库。是否需要等待领域代码完成，取决于迁移类型：

- **向后兼容的增量迁移**：只新增独立表、可空字段或索引，不破坏现有应用行为。专用库验证通过并完成共享库发布前检查后，可先迁移空表结构，再由领域 Owner 开发和验证依赖该结构的代码。
- **破坏性或强耦合迁移**：删除或重命名字段、收紧非空约束、改变现有数据语义，或必须与应用代码同时生效。此类迁移必须等待兼容性验证，并制定应用发布顺序和恢复方案。

V004 只新增 `external_data_snapshot`、`data_sync_log` 两张独立表，不修改 V001 至 V003 的现有表，不包含种子数据，也不建立物理外键，因此属于向后兼容的增量迁移。D 不需要先向 A 提供业务数据；共享库迁移只创建空表结构。

V004 进入共享库前必须同时满足：

```text
[ ] 专用验证库的首次 migrate、重复 migrate、结构和约束用例全部通过并保存证据
[ ] A 只读确认共享 cinewise 库当前 Flyway 历史干净、版本和 checksum 与已发布迁移一致
[ ] 已完成可用备份或时间点恢复确认，并通知受影响成员迁移窗口
[ ] A 再次确认本次 SQL 对当前应用向后兼容
```

共享库迁移由 A 使用独立迁移账号在一次性受控步骤中执行。不得使用日常 `cinewise_app` 账号，不得把共享环境的 `FLYWAY_ENABLED` 永久改为 `true`，也不得依靠应用重启自动建表。

推荐顺序：

```text
验证库通过
  → A 检查共享库历史与备份
  → A 单独执行 V004
  → A 验证 flyway_schema_history 和实际结构
  → D 使用日常应用账号完成持久层与真实 MySQL 集成测试
  → 部署依赖 V004 的应用代码
  → 健康检查与业务冒烟
```

若迁移不是向后兼容的增量迁移，则不得套用以上顺序，必须先完成领域代码兼容性验证和联合发布方案。

迁移成功后，A 保存共享库的版本、checksum、执行时间、结构检查和冒烟证据；V004 保持冻结。执行失败时不得修改 V004 重试，应停止应用发布并根据失败阶段使用备份恢复或新增向前修复迁移。

每次验证至少记录：

```text
[ ] 使用的 MySQL 版本和空数据库标识
[ ] 执行前 Flyway info 结果
[ ] migrate 成功结果与 flyway_schema_history 记录
[ ] 表、列、主键、UNIQUE、普通索引、CHECK 的实际检查结果
[ ] 数据库、表和字符串列的实际字符集与排序规则检查结果
[ ] 应用或 Flyway 再执行一次后的结果：不得重复建表或改写历史版本
[ ] 失败时的错误摘要、处理结论与是否需要回退 SQL 草案
```

真实 MySQL 验证通过后，才可以在对应 OpenSpec 的任务中勾选“空 MySQL 执行迁移”。

### 6.1 字符集与排序规则核验

新建表不得只写`DEFAULT CHARSET = utf8mb4`并依赖数据库或服务器默认排序规则。所有后续`CREATE TABLE`必须显式使用：

```sql
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
```

发现已执行的历史迁移缺少显式`COLLATE`时，按以下顺序处理：

1. 不修改、改名或重新计算已执行迁移的SQL文件。
2. 先只读核验目标数据库默认排序规则、全部相关表的`TABLE_COLLATION`，以及字符串列的`COLLATION_NAME`。
3. 如果数据库、相关表和字符串列实际均为`utf8mb4_0900_ai_ci`，记录验证证据即可，不为了补写DDL而新增无效迁移。
4. 如果任一实际排序规则不一致，由A评估数据量、索引、锁表时间和回滚方案，再新增向前迁移；不得直接手工修改共享库。
5. 评审范围必须覆盖同批迁移创建的全部表，不能只检查报告中点名的单表。

推荐使用以下只读SQL保存实际证据：

```sql
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
  FROM information_schema.SCHEMATA
 WHERE SCHEMA_NAME = DATABASE();

SELECT TABLE_NAME, TABLE_COLLATION
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
 ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND COLLATION_NAME IS NOT NULL
   AND COLLATION_NAME <> 'utf8mb4_0900_ai_ci'
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

V001至V003首次在云端MySQL 8.4.11执行后已按上述SQL核验：数据库、10张业务表、Flyway历史表及所有字符串列均符合`utf8mb4_0900_ai_ci`。因此历史文件保持不变，未新增仅用于重复转换排序规则的迁移。

## 7. 禁止事项

- 不把 `MYSQL_ROOT_PASSWORD`、业务账号密码、JWT、SSH 私钥或云数据库连接串提交到 Git。
- 不把 AI 的“通过”当作执行授权。
- 不在共享 `cinewise` 库、生产库或已有联调数据的数据库上做首次迁移验证；文档明确指定且通过环境守卫的 A 专用 `cinewise_migration_check` 云端验证库除外。
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
MySQL：8.4.x，云端 cinewise_migration_check 空库
migrate：通过 / 失败
validate：通过 / 失败
重复执行：通过 / 失败
结构检查：通过 / 失败
字符集与排序规则：通过 / 失败
未验证项与原因：<内容>
记录人：A
```
