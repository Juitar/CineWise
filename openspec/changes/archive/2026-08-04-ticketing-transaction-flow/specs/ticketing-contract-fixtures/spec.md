# Ticketing Contract Fixtures Spec

## ADDED Requirements

### Requirement: A的票务页面REST夹具必须复用权威响应契约

系统 SHALL 提供场次、座位、订单、支付、电子票、退票和关键错误的版本化JSON夹具。每个夹具 SHALL 使用当前`Result<T>`、`PageResult<T>`和对应REST响应DTO，不得增加仅供Mock使用的业务字段。

#### Scenario: C加载票务成功夹具

- GIVEN A的票务页面需要联调，且C需要通过公共请求层接入A的票务REST
- WHEN C加载成功夹具
- THEN 业务ID均为字符串、金额均为两位小数字符串、时间均为ISO 8601
- AND 场次、座位、订单、支付、电子票和退票状态与A的REST枚举同名

#### Scenario: C加载票务错误夹具

- GIVEN A的票务页面需要验证座位冲突、幂等参数不一致、订单不存在或未登录，且C的公共请求层需要正确传递错误
- WHEN C加载对应错误夹具
- THEN 返回当前稳定数值错误码、用户可读消息和非空示例`traceId`
- AND 不返回交易数据或泄露资源是否属于其他用户

#### Scenario: C加载未登录目标夹具

- GIVEN C已确认未登录最终使用完整`Result` JSON，但当前安全骨架仍可能返回空HTTP 401
- WHEN C加载`unauthenticated-error.json`
- THEN 该夹具被标记为依赖后续自定义`AuthenticationEntryPoint`的目标格式
- AND 在C的认证实现合并前不得宣称当前运行时已支持该响应体

### Requirement: B的票务工具夹具必须遵守公共ToolResult

系统 SHALL 提供`queryShows/createOrder/queryOrder`联调夹具。公共包装 SHALL 仅使用冻结的`ToolResult<T>`字段，业务字段 SHALL 仅位于`data`，支付 SHALL NOT 被表示为Agent工具。

#### Scenario: B加载动态场次成功夹具

- GIVEN B需要验证查询节点、时效校验和结果渲染
- WHEN B加载`queryShows`成功夹具
- THEN 结果包含`basePrice`及公共`dataAt/expiresAt`
- AND `expiresAt`晚于`dataAt`且不晚于候选场次开始时间

#### Scenario: B加载建单幂等恢复夹具

- GIVEN 建单首次结果与使用原请求标识恢复的结果均已提供
- WHEN B比较两份夹具
- THEN 两者返回同一`orderId/orderNo/status/totalAmount/expireTime`
- AND 不出现`replayed`等未冻结字段，也不建议重新执行写工具

#### Scenario: B加载交易失败夹具

- GIVEN 座位冲突或订单不存在
- WHEN B加载失败夹具
- THEN 结果包含稳定`errorCode/retryable/replanSuggested/suggestedNextAction`
- AND 建单失败的`retryable=false`，不得自动生成新幂等键重试

### Requirement: OpenAPI与夹具必须自动防漂移

系统 SHALL 在后端测试中解析全部票务JSON夹具并查询实际`/v3/api-docs`。测试 SHALL 核对受保护路径、`Idempotency-Key`、空支付请求体、关键Schema以及敏感字段缺失。

#### Scenario: 执行票务契约回归

- GIVEN 当前分支包含票务Controller、DTO和联调夹具
- WHEN 执行后端契约测试
- THEN 全部JSON可解析且关键字段类型、状态、金额和恢复语义符合规范
- AND OpenAPI声明Cookie认证、写接口幂等Header及支付接口无请求体
- AND OpenAPI与夹具均不包含模拟密码、JWT、Cookie值或真实密钥
