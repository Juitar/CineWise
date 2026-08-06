# V014 真实内容与同步审计共享库迁移记录

## 执行范围

- 目标迁移：`V014__extend_real_content_and_sync_audit.sql`
- OpenSpec：`openspec/changes/real-content-coverage-completion/`
- 领域 Owner：D / content；Flyway 发布负责人：A
- 执行日期：2026-08-06
- 数据库版本：MySQL 8.4.11
- SQL SHA-256：`4DA9FAAEE01BD013C53F2DCC9EC9331A5DB12A583861C0803A90E3ED71FF4190`
- Flyway V014 checksum：`-1507904895`

## 发布前检查

- A 专用验证库已完成首次 migrate、validate、重复 migrate、结构/索引/CHECK、字符集及正反约束验证。
- 发布前只读确认共享 `cinewise` 位于 V013，V014 为 Pending，且 `content_identity_mapping` 不存在。
- 未修改日常 `.env`，未启用应用自动迁移；本次由一次性 Flyway 进程执行。
- 本次未独立核验发布前备份或恢复演练证据，不能将其表述为已完成。

## 共享库发布结果

- Flyway 成功将共享 `cinewise` 从 V013 升级到 V014，应用 1 个迁移。
- V014 安装时间为 `2026-08-06 11:24:18`，成功标志为 1，checksum 为 `-1507904895`。
- 发布后 `flyway validate` 成功校验 14 个迁移。
- 发布后重复 migrate 报告 `Schema cinewise is up to date. No migration necessary.`。

## 发布后结构检查

- `movie`、`cinema`、`data_sync_log` 和 `content_identity_mapping` 结构已存在，均为 InnoDB、`utf8mb4_0900_ai_ci`。
- `content_identity_mapping` 的主键、两个唯一键、普通索引和 CHECK 已核对。
- 本次仅新增结构和兼容约束，不写入业务数据。

## 流程偏差记录

- 共享库执行使用了当前可用的 `cinewise_app` 账号；规范推荐的独立共享迁移账号未在本次窗口单独配置。后续迁移应先配置并使用专用迁移账号，避免应用账号承担 DDL 权限。

## 结论

V014 已成功发布至共享 `cinewise`，并完成 Flyway 历史、发布后 validate、重复执行、结构、CHECK 与排序规则核验。V014 自本次成功执行起冻结；后续调整必须新增更高版本的前向迁移。
