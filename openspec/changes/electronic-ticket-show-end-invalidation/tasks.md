# 任务

- [x] A 冻结“结束即失效”的状态与非范围，确认不复用退款事件。
- [x] A 新增有界候选查询、条件失效持久化端口和应用服务；验证：集成测试覆盖边界与状态保持。
- [x] A 新增可配置调度 Job；验证：Job 只调用 Application Service。
- [x] A 更新电子票失效提示与组件测试；验证：失效票不显示二维码且展示结束原因。
- [x] A 新增 V022 失效原因字段及候选索引，并在 MySQL 8.4 验证迁移和 EXPLAIN；验证：验证库与共享库均从 V021 首次迁移到 V022，重复 migrate 无迁移，EXPLAIN 使用 `idx_show_end_time_id` range 且无 temporary/filesort。
- [ ] A 执行后端 verify、前端质量检查、OpenSpec strict 和 diff 检查并记录结果。
