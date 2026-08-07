# H2 测试调度基线

## 背景

H2 测试基线只迁移到 V009。认证同意撤回 outbox 属于 MySQL 专属 V017，但自动调度仍会在 H2 测试上下文启动并查询该表，造成持续报错和 Maven 子进程无法退出。

## 范围

- A 在共享 H2 `test` profile 关闭公共调度。
- 保留各领域对 Job/Application Service 的直接测试能力。

## 非范围

- 不为 H2 补 V017 表或迁移。
- 不修改认证 outbox、画像、Agent 或生产调度行为。

## 验收

- `test` profile 不启动任何 `@Scheduled` 后台任务。
- B 的完整 `backend/mvnw.cmd verify` 可正常结束。
