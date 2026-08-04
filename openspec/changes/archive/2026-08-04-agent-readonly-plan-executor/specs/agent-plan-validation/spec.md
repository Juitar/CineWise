## MODIFIED Requirements

### Requirement: Mock 模型必须返回可复现候选计划

开发和测试环境 SHALL 通过 `ModelGateway` 接口使用 `MockModelGateway`。相同 `clientRequestId`、相同当前文本、相同服务端确认槽位和相同允许工具列表 MUST 返回相同的候选计划和节点顺序。确认的 `movieId`、`cinemaId`、`date` 完整且 `rankMoviePlan` 获得允许时，Mock MUST 生成 `rankMoviePlan` 及其下游 `RENDER_RESULT`；缺少必填槽位时，Mock MUST 只生成询问第一项缺失字段的 `ASK_USER`。所有 Mock 候选结果仍 MUST 由主控使用当前服务端 `PlanValidationContext` 经过 `PlanSchemaValidator` 校验后才能执行。

#### Scenario: 重复生成完整槽位的 Mock 推荐计划

- **WHEN** 使用相同请求标识、当前文本、确认槽位和工具白名单重复调用 `MockModelGateway`
- **THEN** 系统返回相同 `planId`、版本、`rankMoviePlan → RENDER_RESULT` 节点内容和节点顺序
- **AND** 主控仍使用服务端上下文重新校验候选计划

#### Scenario: 缺少必填槽位时生成最小追问计划

- **WHEN** Mock 请求缺少 `rankMoviePlan` 的一项或多项必填槽位
- **THEN** 系统只生成询问白名单顺序中第一项缺失字段的 `ASK_USER`
- **AND** 不生成 `rankMoviePlan` 或其他工具节点

#### Scenario: Mock 计划校验失败

- **WHEN** Mock 夹具产生不符合计划规则的候选结果
- **THEN** 主控返回结构化校验失败
- **AND** 不因为结果来自 Mock、或网关曾附带校验结果而绕过服务端当前上下文校验
