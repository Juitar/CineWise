# 认证登录迁移申请材料

> 本文件由 C 准备，用于向 A 申请迁移评审。它只描述业务字段、索引、约束和验证场景，不是 Flyway SQL，不构成执行授权。

## 1. 申请信息

- OpenSpec change：`openspec/changes/password-login-and-session/`
- 领域 Owner：C
- 涉及表：`sys_user`、`sys_login_log`
- 申请版本：`V006`，A 已于 2026-08-03 正式分配；`V005` 已由票务迁移占用，不得重复使用
- 迁移文件：`backend/src/main/resources/db/migration/V006__create_auth_user_and_login_log_tables.sql`
- 非范围：验证码、注册邀请码、邀请码使用记录、真实账号、密码、JWT 和演示种子

### 1.1 迁移拆分结论

- `V006` 一次创建当前密码登录必需的 `sys_user`、`sys_login_log`，两表同属认证模块且需要一起完成真实登录验收，无需再拆成两个版本。
- 注册和邮箱验证码不在本次 OpenSpec 范围；后续 `sys_email_verify_code`、`sys_registration_invite`、`sys_registration_invite_use` 随注册变更申请新的 Flyway 版本，不提前放入 `V006`。
- C 在本地编写 SQL 草案并按 A 的评审结论调整；A 审查通过前不得提交、推送或执行。`V006` 一旦共享或执行，后续修正只能新增向前迁移，不得修改历史文件。
- `V006` 不包含演示账号、真实邮箱、密码散列或其他种子数据。

## 2. T01 `sys_user`

### 2.1 字段

| 字段 | MySQL 类型 | 可空 | 默认值 | 业务规则 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 无 | 主键；应用使用 MyBatis-Plus `ASSIGN_ID` 生成，不得 `AUTO_INCREMENT` |
| `email` | `VARCHAR(255)` | 否 | 无 | 规范化邮箱；全局唯一；本期按后端总系分直接保存并通过数据库权限和日志脱敏保护 |
| `password_hash` | `VARCHAR(255)` | 否 | 无 | 只保存 BCrypt 散列，不保存或记录明文 |
| `nickname` | `VARCHAR(64)` | 是 | `NULL` | REST 响应为空时由认证应用服务提供安全展示值 |
| `role_code` | `VARCHAR(16)` | 否 | `'USER'` | 仅 `USER`、`ADMIN` |
| `status` | `VARCHAR(16)` | 否 | `'NORMAL'` | 本次仅 `NORMAL`、`DISABLED`、`LOCKED` |
| `email_verified` | `TINYINT(1)` | 否 | `0` | 仅 `0`、`1` |
| `token_version` | `BIGINT` | 否 | `0` | 登出、改密或禁用后递增；不得为负数 |
| `privacy_policy_version` | `VARCHAR(32)` | 否 | 无 | 账号已同意的政策版本；演示账号必须显式提供 |
| `privacy_accepted_at` | `DATETIME(3)` | 否 | 无 | 账号同意时间；按 `Asia/Shanghai` 解释 |
| `version` | `BIGINT` | 否 | `0` | 乐观锁版本；不得为负数 |
| `create_time` | `DATETIME(3)` | 否 | 无 | 创建时间，毫秒精度 |
| `update_time` | `DATETIME(3)` | 否 | 无 | 更新时间，毫秒精度 |

### 2.2 索引和约束

- 主键：`PRIMARY KEY (id)`。
- 唯一索引：`uk_sys_user_email (email)`。
- 普通索引：`idx_sys_user_status_update (status, update_time)`。
- 建议 CHECK：`role_code IN ('USER','ADMIN')`。
- 建议 CHECK：`status IN ('NORMAL','DISABLED','LOCKED')`。
- 建议 CHECK：`email_verified IN (0,1)`。
- 建议 CHECK：`token_version >= 0 AND version >= 0`。
- 不建立跨模块物理外键。
- 表显式使用 `InnoDB`、`utf8mb4`、`utf8mb4_0900_ai_ci`。

### 2.3 已确认规则

1. **邮箱存储**：本期按后端总系分保存规范化邮箱，依赖数据库权限、备份保护和日志脱敏；若未来增加应用层加密，必须新增查询摘要列并通过独立 OpenSpec 和向前迁移处理。
2. **状态枚举**：仅使用 `NORMAL/DISABLED/LOCKED`。
3. **演示账号**：不允许在 Flyway 中写账号密码；由显式开启的认证种子初始化器从环境变量读取用户/管理员凭据。

## 3. T05 `sys_login_log`

### 3.1 字段

| 字段 | MySQL 类型 | 可空 | 默认值 | 业务规则 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 无 | 主键；应用使用 `ASSIGN_ID` 生成 |
| `user_id` | `BIGINT` | 是 | `NULL` | 登录失败且无法识别账号时为空；只逻辑关联 `sys_user.id` |
| `login_type` | `VARCHAR(32)` | 否 | 无 | 本次仅 `PASSWORD`、`ADMIN_PASSWORD` |
| `success` | `TINYINT(1)` | 否 | 无 | 仅 `0`、`1` |
| `failure_code` | `VARCHAR(32)` | 是 | `NULL` | 保存稳定错误码，不保存错误堆栈或凭据 |
| `ip_hash` | `VARCHAR(128)` | 是 | `NULL` | IP 的带服务端密钥摘要，不保存完整 IP |
| `user_agent_summary` | `VARCHAR(255)` | 是 | `NULL` | 截断和清理后的最小摘要，不保存完整 Header |
| `trace_id` | `VARCHAR(64)` | 否 | 无 | 请求追踪标识 |
| `create_time` | `DATETIME(3)` | 否 | 无 | 登录结果产生时间，毫秒精度 |

### 3.2 索引和约束

- 主键：`PRIMARY KEY (id)`。
- 普通索引：`idx_login_user_time (user_id, create_time)`。
- 普通索引：`idx_login_result_time (success, create_time)`。
- 普通索引：`idx_login_trace (trace_id)`。
- 清理索引：`idx_login_cleanup_create_time (create_time)`。
- 建议 CHECK：`login_type IN ('PASSWORD','ADMIN_PASSWORD')`。
- 建议 CHECK：`success IN (0,1)`。
- 登录结果一致性 CHECK：成功日志必须满足 `user_id IS NOT NULL AND failure_code IS NULL`；失败日志必须满足 `failure_code IS NOT NULL`，失败时 `user_id` 可以为空或保留已识别账号 ID。
- `user_id` 只做逻辑关联，不建立外键。
- 表显式使用 `InnoDB`、`utf8mb4`、`utf8mb4_0900_ai_ci`。

### 3.3 生命周期

- 默认保留 30 天，通过正整数配置管理；到期记录由认证 Application Service 按 `create_time` 条件物理删除，不在迁移 SQL 中加入定时清理逻辑。
- 登录日志只用于安全审计和排查，不作为用户会话是否有效的依据。
- 本表是只追加、不可修改的审计日志，因此只保留 `create_time`，不增加没有业务含义的 `update_time`；A 已确认该只追加表例外。
- 30 天到期物理删除是“订单、支付、电子票、退款和审计记录不得物理删除”通用规范的有限保留期例外。该例外仅适用于 `sys_login_log`，因为它不承载交易事实；30 天内不得随意物理删除，也不提供按用户或单条日志删除的业务入口，不影响交易审计记录的长期保留要求。

## 4. 非迁移演示账号初始化

- 不在结构迁移或数据迁移中写入真实账号、明文密码或可复用默认密码。
- 建议增加 `AUTH_DEMO_SEED_ENABLED=false`，只有显式开启时才运行认证种子初始化器。
- 用户和管理员邮箱、密码、昵称、隐私政策版本从环境变量或只读 Secret 读取，不提供生产可用默认密码。
- 本地开发可以配置 `user@cinewise.test`、`admin@cinewise.test`；正式演示可在首次初始化前改为真实可收信的测试邮箱，不改变代码和表结构。
- 初始化器只补不存在的账号；重复运行不得修改已有密码、角色、状态或 `token_version`。
- 初始化器不得因环境变量变化自动修改已存在账号的邮箱；已有数据库改邮箱必须经后续账号用例，纯开发空库可以重新初始化。
- 初始化过程只记录创建数量和内部 ID，不记录邮箱、密码或密码散列。

## 5. A 需要验证的场景

1. 空 MySQL 8.4 执行迁移成功，Flyway 历史版本正确。
2. 重复执行不重复建表、不修改历史迁移。
3. 两张表的字段类型、可空性、默认值、主键、索引和 CHECK 与确认后的本文件一致，包含 `idx_login_cleanup_create_time`。
4. 表及所有字符串列实际使用 `utf8mb4_0900_ai_ci`。
5. 两个并发请求插入同一规范化邮箱时，唯一索引只允许一个成功。
6. `token_version` 和 `version` 不允许负数；非法角色、账号状态、登录类型和布尔值被 CHECK 拒绝。
7. 成功日志缺少 `user_id` 或包含 `failure_code` 时被拒绝；失败日志缺少 `failure_code` 时被拒绝；失败日志允许 `user_id=NULL`。
8. 不存在外键、真实账号、密码、JWT、邀请码或其他演示业务数据。

## 6. 当前确认状态

- C 已确认：本次只需要 T01、T05，不创建 T02-T04。
- C 已确认：规范化邮箱存储、`NORMAL/DISABLED/LOCKED`、登录日志只保留 `create_time`、30 天有限保留期物理删除例外，以及支持假/真邮箱的环境变量认证种子。
- A 已确认：为本次认证迁移分配 `V006`，文件名为 `V006__create_auth_user_and_login_log_tables.sql`，且只包含 `sys_user`、`sys_login_log`，不包含演示账号或其他种子数据；`sys_login_log` 作为只追加日志不增加 `update_time`。
- C 负责：按 A 的静态审查意见修订本地 SQL 草案，保持未提交、未推送、未执行，并重新私下交给 A。
- 待 A 完成：固化最终 SQL、AI 只读复核、CHECK 兼容性、空 MySQL 8.4 验证，并由 A 将验证通过的最终文件直提 `dev`。
