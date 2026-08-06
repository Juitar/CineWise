# V014 真实内容与同步审计迁移验证记录

## 基本信息

- 迁移：`V014__extend_real_content_and_sync_audit.sql`
- OpenSpec：`real-content-coverage-completion`
- 领域 Owner：D / content
- Flyway 版本分配、静态审查与验证负责人：A
- 验证日期：2026-08-06
- MySQL：8.4.11
- A 专用验证库：`cinewise_migration_check`
- 验证账号：`cinewise_migrator`
- SQL SHA-256：`4DA9FAAEE01BD013C53F2DCC9EC9331A5DB12A583861C0803A90E3ED71FF4190`
- Flyway V014 checksum：`-1507904895`

## 范围与环境守卫

- V014 新增影片资料字段、`content_identity_mapping`、影院城市字段和同步审计字段，并加入兼容的 PENDING 状态 CHECK。
- 不修改历史迁移，不包含回填、种子、账号、密码、密钥或物理外键。
- 目标数据库和账号已核对为 `cinewise_migration_check` / `cinewise_migrator`；未连接共享库执行本次验证。
- 验证结束后所有测试事务均回滚，未留下业务测试数据。

## Flyway 验证

- 执行前 V001–V013 为 Success，V014 为 Pending。
- 首次 migrate 成功，V014 安装时间为 `2026-08-06 11:17:12`。
- `flyway validate` 成功校验 14 个迁移。
- 重复 migrate 报告 `Schema cinewise_migration_check is up to date. No migration necessary.`。

## 实际结构与约束

- `movie` 新增 `poster_url`、`summary`、`release_status`、`release_date` 及发布状态索引和 CHECK。
- `content_identity_mapping` 创建成功，包含主键、外部身份唯一键、ACTIVE 内部 ID 唯一键、查询索引和 5 条 CHECK。
- `cinema` 新增 `city_name`、`provider_city_id` 及对应索引。
- `data_sync_log` 新增城市、失败分类、租约字段及恢复索引，并替换为兼容五状态 CHECK。
- 目标表均为 InnoDB、`utf8mb4_0900_ai_ci`；数据库和相关字符串列排序规则核验通过。

## 正反约束用例

- 合法 PENDING、旧 RUNNING、FAILED、PARTIAL、SUCCESS 均可在事务中写入。
- 合法 ACTIVE/INVALID 身份映射可写入。
- 非法 PENDING 计数、非法状态、租约半填、负数计数、处理数超总数、非法发布状态、非法映射状态/类型/失效字段均被 CHECK 拒绝。
- 外部身份重复和同 Provider/资源类型下重复 ACTIVE 内部 ID 均被唯一键拒绝。
- 测试结束后 `data_sync_log`、`content_identity_mapping`、`movie`、`cinema` 均无测试残留。

## 结论

V014 已在 A 专用 MySQL 8.4.11 验证库完成首次 migrate、validate、重复 migrate、结构、索引、CHECK、字符集、排序规则及正反约束验证，结果通过。V014 的严格租约、错误分类和失败字段要求仍按 OpenSpec 由后续 V015 处理。本记录不替代共享库发布记录。
