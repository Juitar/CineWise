# 任务

- [x] A 确认 H2 V009 基线与 V017 outbox 表不兼容，不补 H2 迁移。
- [x] A 在 H2 `test` profile 关闭公共调度。
- [x] A 运行完整后端 verify，确认无 outbox 缺表调度日志且 Maven 正常退出；验证：`backend\\mvnw.cmd verify` 通过。
- [x] A 执行 OpenSpec strict 与 diff 检查；验证：均通过。
