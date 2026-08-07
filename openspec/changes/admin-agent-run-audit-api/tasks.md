## 1. 查询模型和持久化边界

- [x] 1.1 Owner：B；已记录 `planId`、`planVersion`、`targetName`、`toolStatus`、`errorCode`、`errorSummary` 的来源和 C 侧差异；验证：OpenSpec design/spec 写明不伪造字段。
- [x] 1.2 Owner：B；新增 Agent 管理运行查询的 Application 查询输入、视图和 Repository 端口，限定白名单筛选、固定排序和脱敏字段；验证：`AdminAgentRunQueryServiceTest` 通过。
- [x] 1.3 Owner：B；在 Agent 自有 MyBatis Mapper/Repository 中实现运行计数、分页、详情和步骤读取，不选择敏感 JSON 列；验证：`backend/mvnw.cmd verify` 通过。

## 2. API 和权限

- [x] 2.1 Owner：B；实现管理查询服务，复核 ADMIN、调用 C 的公开 `UserAdminQueryPort` 获取关键词和脱敏显示值，并映射安全节点摘要；验证：服务测试覆盖 ADMIN、USER、分页和 404。
- [x] 2.2 Owner：B；新增 `/api/v1/admin/agent-runs` 列表和 `/{runId}` 详情 Controller/DTO，复用 `Result`、`PageResult`、现有 404 和时区格式；验证：应用上下文和最终 `verify` 通过。

## 3. 测试、夹具和交付检查

- [x] 3.1 Owner：B；新增 ADMIN 列表、USER 403、404、分页排序和敏感事件字段不泄露测试；验证：`AdminAgentRunQueryServiceTest` 通过。
- [x] 3.2 Owner：B；新增 C 可直接使用的列表与详情固定 JSON 夹具，字段与真实 API DTO 一致；验证：`AdminAgentRunFixtureTest` 通过。
- [x] 3.3 Owner：B；记录 `WAITING_LOCATION` 与 C 现有四状态筛选的具体差异，不修改或猜测前端映射；验证：OpenSpec design/spec 列出字段和原因。
- [x] 3.4 Owner：B；运行 OpenSpec 严格校验、相关后端测试、最终 `backend/mvnw.cmd verify`、`git diff --check` 和状态核对；验证：最终 `verify` 退出码为 0，不提交、不推送、不归档。
