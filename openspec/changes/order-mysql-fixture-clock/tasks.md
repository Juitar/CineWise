# 任务

- [x] A 在支付和退款 MySQL 测试中注入与固定种子一致的 `Clock`。
- [x] A 将场次夹具查询改为使用固定业务时间参数。
- [x] A 在隔离 MySQL 8.4 运行支付、退款并发测试；验证：两个测试共 2/2 通过。
- [x] A 执行 OpenSpec strict、后端校验和 diff 检查并记录结果；验证：`backend\\mvnw.cmd verify`、MySQL 8.4 两项并发测试、OpenSpec strict 与 `git diff --check` 均通过。
