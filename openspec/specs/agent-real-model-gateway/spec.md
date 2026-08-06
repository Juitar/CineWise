# agent-real-model-gateway Specification

## Purpose
TBD - created by archiving change agent-acceptance-critical-path. Update Purpose after archive.
## Requirements
### Requirement: 真实模型调用仅使用运行环境密钥
系统 SHALL 在显式启用真实模型时，通过 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` 和 `DEEPSEEK_BASE_URL` 调用 DeepSeek 的非流式 `/chat/completions`；API Key 不得写入源码、配置样例的值、日志、SSE 或持久化数据。

#### Scenario: 配置齐全时生成候选计划
- **WHEN** 真实模型开关开启且运行环境提供完整 DeepSeek 配置
- **THEN** 系统使用 `Authorization: Bearer` 调用指定模型，并把返回 JSON 解析为候选计划后交给服务端计划校验

#### Scenario: 配置或模型响应无效
- **WHEN** 缺少密钥、HTTP 调用失败、超时、choices 为空或返回内容不是所需 JSON
- **THEN** 系统返回安全的模型失败结果且不记录密钥、原始供应商响应或用户身份

### Requirement: Mock 模型保留为确定性测试实现
系统 SHALL 保留 `MockModelGateway`，相同请求在测试和默认开发装配中返回相同的候选计划和回复。

#### Scenario: 未启用真实模型
- **WHEN** 真实模型未显式启用
- **THEN** Spring 装配 Mock 实现，Agent 单元测试不发起网络调用
