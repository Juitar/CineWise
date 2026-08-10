# Tasks

- [x] 1. C：固定同意快照和撤回事件类型，同步 D 调用方、fallback 与夹具；验证：后端编译和 profile 定向测试。
- [x] 2. C：实现同意记录、CAS 撤回、同事务 outbox、自动重试和人工恢复；验证：应用层单元测试。
- [x] 3. C：经 `modules/profile` 接入个人中心标签和个性化开关；验证：前端 Hook 和页面测试。
- [x] 4. C：完成 MySQL、Redis、前后端全量验证；验证：MySQL 8.4 运行 `31147996467`、Redis 7.4
  运行 `31147998267`、后端运行 `31147973476`、前端运行 `31147973262` 均通过；命令、通过数、清理
  情况和本机环境限制见 `verification.md`。
- [x] 5. C：完成 OpenSpec 严格校验、Maven verify、`git diff --check` 和变更范围核对。
