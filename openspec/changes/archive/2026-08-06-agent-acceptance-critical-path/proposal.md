## Why

现有 Agent 已能校验候选计划、执行已确认的 `rankMoviePlan` 并复用确认建单，但还不能以真实模型生成计划，也没有统一的可插拔多工具执行入口。验收主流程因此无法覆盖“模型计划—多个查询工具—失败后重新规划—卡片—确认建单”。

## What Changes

- 新增 DeepSeek `ModelGateway` 实现，使用 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`、`DEEPSEEK_BASE_URL` 本地环境变量或 GitHub Actions Secret 配置；保留确定性 `MockModelGateway` 用于测试。
- 将已存在的工具定义、白名单和只读适配器收敛为统一可注册执行接口，支持 `rankMoviePlan`、`queryAvailableDates`、`queryShows` 和确认后的 `createOrder`；`querySeats` 仅保留可插拔扩展位，不作为本 change 的验收依赖，选座通过 `SELECT_SEATS` 业务意图卡片跳转购票页。
- 完成多工具调用事件、失败重规划限制和写工具不自动重试规则；正式业务 DTO 未确认时只提供注册接口、夹具和 Mock，不猜测字段。
- 补齐 Agent 回复到 SSE 的 `QUESTION`、`PLAN_CARD`、`PROGRESS`、`ERROR`、`tool.start`、`tool.complete` 映射与测试；选座使用 `card` 事件中的 `BUSINESS_INTENT` 卡片。
- 复用既有确认动作和 A 的 `createOrder` 公共 Application API，不新增订单写入逻辑。

## Capabilities

### New Capabilities

- `agent-real-model-gateway`: DeepSeek 真实模型调用、环境变量配置、JSON 计划解析和安全失败映射。
- `agent-pluggable-tool-execution`: 多工具注册、参数校验、执行顺序、工具事件、失败重规划和写工具恢复规则。
- `agent-acceptance-sse-replies`: 核心回复类型与持久化 SSE 事件的可验证映射。

### Modified Capabilities

- 无。

## Impact

- 主要修改 `backend/src/main/java/com/miaoyu/ticket/agent/**` 和对应单元测试、测试夹具、`application.yml`、`.env.example`。
- 依赖 DeepSeek OpenAI 兼容 `POST /chat/completions`，不提交 API Key。
- A 需继续提供已存在的 `createOrder` 公共 Tool/Application API；A 的日期、场次、座位 DTO/Application API 与 D 的正式推荐 DTO 未确认前，本 change 不实现其业务逻辑或跨模块访问。
