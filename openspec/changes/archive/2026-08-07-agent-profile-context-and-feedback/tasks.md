## 1. 受控画像上下文

- [x] 1.1 B 在 `MultiToolSupervisor` 前新增内部画像预取与最小转换类型；验证：关闭、无同意和失败都以空上下文继续，且不传 userId。
- [x] 1.2 B 扩展模型计划请求并接入预取；验证：画像不是注册工具、节点、运行或 SSE，已启用 tags 才进入请求。

## 2. 稳定方案和确认反馈

- [x] 2.1 B 在有效候选计划进入执行前生成服务器小写 UUID planId；验证：运行、卡片、确认动作复用该值。
- [x] 2.2 B 在确认 CAS 实际保存 REJECTED/SUCCEEDED 后调用 D；验证：eventId 为 actionId，时间为 UTC，其他终态和 CAS 读胜者均不调用。

## 3. 测试与交付检查

- [x] 3.1 B 补充 Supervisor 和确认服务定向测试；验证：覆盖画像开关、失败降级、UUID、重复确认和画像异常。
- [x] 3.2 B 运行定向 Maven 测试、`backend/mvnw.cmd verify`、`openspec validate agent-profile-context-and-feedback --strict` 和 `git diff --check`；验证：全部通过或记录未验证原因。
