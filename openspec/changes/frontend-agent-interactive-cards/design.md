# 设计

## 类型和安全边界

`contract.ts` 按 PR #160 的正式 DTO 校验 `QUESTION` 和 `PLAN_CARD`。`projection.ts` 将合法 payload 转换为明确的 `question`、`plans` 和数据状态展示模型；组件仍不接收 `AgentEvent` 或原始 payload。

问题选项仅保留 `optionId/label/value`，点击后把服务端给出的 `value` 交给现有 `submit(content)`。自由文本沿用同一入口，由 Hook 每次生成一个 `clientRequestId`。组件在提交中和卡片过期后禁用操作，不创建第二套 API。

方案只按服务端数组顺序展示前三项，不在前端评分、排序或补字段。每项保留影片、影院、场次、价格、时间、评分、理由、距离、来源、数据时间、有效期、过期和可购状态。顶层保留来源、数据时间、有效期、降级、过期、缺失因素和放宽建议。

## 组件和状态

`AgentDisplayItemView` 增加 `onAnswer(itemKey, answer)` 回调。`QuestionCard` 使用受控输入，并在首次提交后本地锁定；工作区运行中的统一 `busy` 状态同时禁止所有问题卡再次发送。过期判断只使用已校验 `expiresAt`，不延长服务端有效期。

`PlanCard` 使用语义化列表展示多个方案。不可购或过期方案只读；本次方案卡不直接建单，也不从方案项生成选座 URL。选座仍只由独立 `BUSINESS_INTENT` 投影生成。

确认卡继续走现有 `onConfirm` 和 `useAgentWorkspace.confirm`。`RESULT_UNKNOWN` 仍只查询原 run，不重发确认请求。

## 兼容与恢复

旧的精简推荐夹具仍按已有字段安全展示；PR #160 完整字段存在时展示增强信息。历史消息缺少可验证的外层计划字段时继续安全占位，不从历史文本补造方案。

## 测试

- 协议与投影：完整正式夹具、非法字段、问题选项值、方案状态和未知字段。
- 组件：快捷回答、自由文本、防重复、过期禁用、空方案、完整方案和确认回归。
- Hook/工作区：答案复用现有 submit，运行中禁止重复。
- 执行前端相关测试、类型检查、Lint、构建、OpenSpec strict 和 `git diff --check`。
