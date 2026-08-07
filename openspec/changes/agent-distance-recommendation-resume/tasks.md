## 1. 审查前规划与迁移草案

- [x] 1.1 B：记录 A、C、D 已确认的 REST、状态、隐私、TTL 与恢复契约；验证：OpenSpec strict 通过且无未确认字段。
- [x] 1.2 B：提交 V018 前向 SQL 草案给 A 静态审查；验证：仅替换两个 CHECK、V008 未改、草案未执行。
- [x] 1.3 A：分配/确认 V018 并完成 SQL 静态审查；验证：MySQL 8.4 空库、历史状态和重复 Flyway 验证已通过，V018 已发布并冻结。
- [ ] 1.4 D/公共基础设施：确认位置上传 URL 中 distanceContextId 的访问日志脱敏或禁记；验证：给出可审查的日志配置/测试证据。

## 2. 持久化状态与恢复

- [x] 2.1 B：新增 WAITING_LOCATION 状态和 `id + version + expectedStatus` 的运行 CAS；验证：`AgentLocationWaitingServiceTest` 覆盖非终态、完成时间、重复等待和终态拒绝，定向 Agent 持久化测试通过。
- [ ] 2.2 B：实现以数据库时间和 update_time 为基准的五分钟等待恢复；验证：RUNNING 陈旧恢复不处理等待、等待超时仅恢复一次普通推荐。
- [ ] 2.3 B：在拒绝、上传失败、超时、取消和运行失败路径实现一次清理和正确终态/回退；验证：NOT_FOUND 视为成功，重启后仅依赖 D TTL。

## 3. REST 与推荐恢复

- [ ] 3.1 B：实现距离上下文创建 REST 与 DTO；验证：空请求体、当前用户授权、返回 ID/expiresAt/NEAREST，且无持久化泄漏。
- [ ] 3.2 B：实现位置结果 REST；验证：固定 locationResult 枚举、UPLOADED 同 run 同 planVersion NEAREST 恢复，其他规定分支正确处理。
- [ ] 3.3 B：连接受控 ToolContext 到 `executeRecommendationPlan`；验证：距离 ID 不进入 Command、槽位、模型、SSE、日志、Redis、MySQL 或轨迹。

## 4. 测试与验证

- [ ] 4.1 B：补充状态机、CAS、超时、陈旧恢复、取消/失败、重复清理和旧事件/planVersion 的定向测试。
- [ ] 4.2 B：补充两条 REST、DTO、普通/NEAREST ToolContext、PLAN_CARD/SSE 脱敏和同 run 恢复的定向测试。
- [ ] 4.3 A：在授权后执行 MySQL 8.4 CI；验证：空库 Flyway、旧状态兼容、CAS 冲突、等待恢复和清理分支全部通过。
- [ ] 4.4 B：完成受影响 Maven 测试、`backend/mvnw.cmd verify`、OpenSpec strict 与 `git diff --check`，记录未验证的跨 Owner 风险。
