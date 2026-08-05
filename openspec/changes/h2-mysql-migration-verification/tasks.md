## 1. 范围与设计

- [x] 1.1 A 确认 V010 已执行且不可修改，问题仅为 H2 测试方言边界；验证：MySQL V010 记录与 H2 解析错误证据一致。
- [x] 1.2 确认复用现有一次性 MySQL 8.4 CI，不连接共享库、不新增真实凭据；验证：proposal、design 和 spec 冻结隔离边界。

## 2. 实现

- [x] 2.1 将 H2 test profile 的 Flyway 目标固定为 V009，并注释该边界原因；验证：包含 V010 的完整 `mvn verify` 通过。
- [x] 2.2 新增通用 MySQL latest 迁移守卫，覆盖空库首次 migrate、无 pending/失败历史和重复 migrate；验证：一次性 MySQL 8.4 执行通过。
- [x] 2.3 更新现有 MySQL workflow，创建独立迁移库、显式使用 latest 并运行通用守卫；验证：workflow 静态核对和本地等价命令通过。
- [x] 2.4 更新后端骨架，说明 H2 V009 快速基线与 MySQL 最新迁移权威边界；验证：文档与实际配置一致。

## 3. 验证与交付

- [x] 3.1 `openspec validate h2-mysql-migration-verification --strict --no-interactive`、`git diff --check` 通过。
- [x] 3.2 `backend\\mvnw.cmd verify` 通过，H2 不解析 V010 且现有快速测试保持有效。
- [x] 3.3 一次性 MySQL 8.4 从空库执行 V001 至当前 latest、重复 migrate 和业务 MySQL 测试通过；不连接共享库。
- [x] 3.4 复核提交范围不包含 V010 SQL、迁移验证记录、共享发布记录、`.env`、备份或构建产物；修复通过普通分支 PR 交付。
