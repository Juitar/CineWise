# 用户画像联调验证记录

## 本次补充的 D 侧证据

| 验证项 | 测试文件 | 执行条件 | 验收重点 |
| --- | --- | --- | --- |
| MySQL 画像行为事件 | `ProfileBehaviorMySqlIntegrationTest` | `CINEWISE_MYSQL_PROFILE_IT=true`，A 专用库 | `PLAN` 反馈和 `PAID_ORDER` 写入真实表；相同 `eventId` 重放只有一条记录 |
| Redis 画像摘要 | `RedisProfileSummaryCacheIntegrationTest` | `REDIS_INTEGRATION_ENABLED=true` | 摘要可写入、读取；按用户失效后旧版本键不可读 |
| B 方案反馈 | `AgentConfirmationServiceTest`、`MultiToolSupervisorTest` | 普通 `mvn verify` | 服务端 `planId`、内部预取、最终确认时点、失败降级和无额外 Agent/SSE 写入 |

## MySQL 安全边界

- 画像 MySQL 测试只允许连接 A 提供的 `cinewise_ticketing_concurrency_check` 专用库。
- 测试使用固定的 D 专属用户和事件 ID，并在每次测试前后清理四张画像表中的测试数据。
- 测试关闭 Flyway，只检查已准备好的四张画像表；不修改共享库或迁移文件。
- C 的正式同意查询尚未接入时，测试通过显式的测试同意实现返回 `granted=true`，不代表生产默认同意。

## 尚未由 D 单独宣称完成的事项

- C 的正式同意记录、撤回可靠投递和画像页面联调；
- A 授权后的真实 V010 迁移复核结果；
- B/D 的长期对话偏好确认写入；方案接受/拒绝反馈和摘要预取已由 PR #130 接入。
