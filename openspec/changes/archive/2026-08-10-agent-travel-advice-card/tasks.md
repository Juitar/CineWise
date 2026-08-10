## 1. 协议和安全 DTO

- [x] 1.1 B：新增 `TRAVEL_ADVICE_CARD` 的后端 payload DTO、回复事实和受控结果映射；验证：正常、无快照、降级、过期和空建议的 DTO 单元测试。
- [x] 1.2 B：扩展 `AgentCardEventValidator`、API 卡片响应、持久化事件/SSE 映射，拒绝未知字段和敏感字段；验证：合法卡片可渲染、非法卡片不推进游标的测试。

## 2. Tool 和运行结果接入

- [x] 2.1 B：核对并保留 `getTravelAdvice` 白名单、`readOnly=true`、单一 `SLOT/travelTaskId` 输入和 D 公开 Tool 调用；验证：Adapter 成功、槽位伪造和 D 安全错误映射测试。
- [x] 2.2 B：在 `MultiToolSupervisor` 的成功结果回复路径生成 `TRAVEL_ADVICE_CARD`，不影响 `PLAN_CARD + CREATE_ORDER` 确认路径；验证：Supervisor 定向测试。

## 3. C 协议夹具和投影

- [x] 3.1 B：更新 C 的 `types.ts`、`contract.ts`、`projection.ts`，使实时事件和历史恢复只按 `eventType + payload` 渲染出行建议卡片；验证：前端类型/投影测试。
- [x] 3.2 B：新增 C 可直接使用的 JSON 夹具，覆盖正常、available=false、degraded、expired、空 advice 和敏感字段禁止断言；验证：后端夹具契约测试和前端夹具测试。

## 4. 验证与交付检查

- [x] 4.1 B：执行 `openspec validate agent-travel-advice-card --strict`、相关后端和前端测试；验证：命令退出码为 0。
- [x] 4.2 B：准备交付时只执行一次 `backend/mvnw.cmd verify`，并检查 `git diff --check`、分支和文件范围；验证：`verify` 通过，864 个测试通过、62 个跳过，Checkstyle 和 SpotBugs 通过。
