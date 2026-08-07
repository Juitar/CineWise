# 任务

- [x] B：完成 proposal、design、spec 和 tasks，并执行严格校验。
- [x] B：为运行仓储增加当前用户与当前会话范围内的批量 UUID 查询能力。
- [x] B：将历史消息查询映射为受控的对外 runId，并保持现有分页和归属校验。
- [x] B：更新 C 历史消息夹具和 `AgentCFixtureContractTest`，补充 B 侧映射与安全测试。
- [x] B：运行相关测试、一次完整 `backend\\mvnw.cmd --batch-mode --no-transfer-progress verify`、严格校验和差异检查。
- [ ] C：按已提供的历史消息 runId 实现并测试前端超时恢复；本 change 不修改 C 前端代码。
