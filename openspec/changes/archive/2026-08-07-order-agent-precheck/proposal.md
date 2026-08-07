# Agent 建单确认前只读预检

## 背景

B 已接入 A 的 `CreateOrderTool` 写入口，但确认动作可能在用户等待期间失效。B 需要在调用写 Tool 前，检查 `showId + seatIds` 是否仍满足建单的基本可执行条件，并在不可执行时直接拒绝确认。

## 范围

- A 在 `CreateOrderTool` 提供公开、只读、无副作用的 `validate` Application API。
- A 复用订单最终使用的票务座位查询端口检查场次和座位当前状态。
- 返回仅包含可执行布尔值和稳定安全错误码，不返回金额、库存细节、座位状态、订单或 Entity。
- 补充无写入单元测试、架构边界测试和订单集成回归。

## 非范围

- 不替代 `CreateOrderTool.execute` 的事务内场次、价格、座位和幂等复核；预检结果不是锁座保证。
- 不消费或校验 B 的 actionId、确认存储、SSE、重规划或运行状态。
- 不新增 REST Controller、错误码、表结构、Flyway 或迁移版本。
- 不实现 C 的认证和 JWT。

## Owner 与协作

- Owner：A（order/ticketing）。
- 消费方：B，仅依赖 `com.miaoyu.ticket.order.api.CreateOrderTool.validate` 和类型化 DTO。
- A 只读取所属票务 Application API/Repository；B 不访问 A 的 OrderApplicationService、Entity、Mapper、Repository 或数据库。

## 验收

预检成功时只返回 `executable=true`；场次不存在/停售/已开场、座位不可锁定或参数非法时返回 `executable=false` 与稳定错误码；数据库查询异常返回 306003。任何预检路径都不得执行座位锁定、订单写入或生成业务 ID。
