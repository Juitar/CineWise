# Tasks: agent-confirmed-order-tool

- [x] 1.1 冻结 A/B 公开 Tool、Command、Result、上下文和职责边界；确认不新增 SQL。（Owner: A、B）
- [x] 2.1 实现 `CreateOrderForAgentCommand`、`AgentOrderResult` 和 `CreateOrderTool`，复用订单 Application Service 与当前用户上下文。（Owner: A）
- [x] 2.2 实现成功、业务失败、PROCESSING 和原 clientRequestId 查询恢复映射；禁止自动重试。（Owner: A、B）
- [x] 3.1 补充参数、target、当前用户、幂等重放、失败和结果未知测试。（Owner: A）
- [x] 3.2 执行 Maven verify、OpenSpec strict 和差异检查，记录注释率与未执行的外部环境验证。（Owner: A）
- [ ] 3.3 提交独立 PR，并向 B 提供合入 dev 后的提交号与公开包路径。（Owner: A、B）
