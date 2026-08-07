# 出行模块离线降级回归记录（2026-08-07）

## 本次目的

验证真实天气、路线和邮件投递服务不可用时，出行模块会明确降级，且不会影响后端既有购票、订单和电子票测试。

## 执行配置

以下环境变量仅在当前 PowerShell 进程中设置，未写入 `.env` 或仓库：

```text
AMAP_WEATHER_ENABLED=false
AMAP_WEATHER_KEY=
AMAP_ROUTE_ENABLED=false
AMAP_ROUTE_KEY=
AUTH_MAIL_DELIVERY_ENABLED=false
AUTH_VERIFICATION_SMTP_ENABLED=false
```

## 执行命令与结果

```powershell
Set-Location backend
.\mvnw.cmd verify
.\mvnw.cmd verify -DskipTests
```

- 测试报告：191 个测试类，738 通过、0 失败、0 错误、51 跳过。
- 跳过项：受 MySQL 或外部环境开关控制的集成测试；未把跳过项视为通过。
- Checkstyle：0 项违规。
- SpotBugs：0 个问题。
- JaCoCo：已生成覆盖率报告。
- OpenSpec：`openspec validate travel-reminder-experience --strict` 通过。
- Git：`git diff --check` 通过；执行前工作区干净。

## 已验证的离线行为

- 天气查询在真实 Provider 关闭或不可用时使用缓存、版本化 Demo 或明确不可用结果，并保留通用交通建议。
- 路线真实 Provider 未配置、超时或返回异常时，返回带来源和降级标记的 Demo 结果或 `307001`，不保存一次性起点、路线折线或途经点。
- 邮件投递使用 Mock/不可用 Provider 时按 `deliveryKey` 查询恢复；结果为 `UNKNOWN` 时不会盲目重发。
- 所有动态出行结果都要求携带来源、数据时间、过期时间和降级信息；Demo 数据不表示实时事实。

## 尚未完成的验证

- D：历史 `cinema_id=NULL` 任务的路线查询拒绝规则尚未实现或经 MySQL 验证。
- B、D：联调 Agent 对话中的只读出行建议，确认没有 `agent_*` 或出行数据写入。
- C、D：联调任务、建议、刷新、路线接口，以及定位授权和地图渲染。
- A、C、D：在 MySQL、Redis 和 Mock 邮件 Provider 的联调环境执行事件、缓存、通知恢复和隐私验证。

本记录仅说明 D 本地离线自动化验证结果，不代替 A、B、C 的联调确认或生产环境验证。
