## 1. 实现

- [x] 1.1 任务详情通过公开 Application Port 聚合，并统一映射 `207004`（Owner：D）
- [x] 1.2 建议响应改为类型化字段，保留旧 JSON 兼容字段（Owner：D）
- [x] 1.3 提醒时间更新改为最小已提交任务响应，不查询详情摘要（Owner：D）
- [x] 1.4 恢复原 `travel-reminder-experience` 的既有约束（Owner：D）

## 2. 验证

- [x] 2.1 MockMvc 覆盖五类建议夹具和更新成功后的摘要不可用场景（Owner：D）
- [x] 2.2 运行相关测试、`openspec validate travel-public-rest-contracts --strict` 与 `git diff --check`（Owner：D）
