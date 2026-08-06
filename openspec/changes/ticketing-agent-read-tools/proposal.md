# 票务 Agent 只读日期与场次工具

## Why

`MultiToolSupervisor` 已提供已登记只读工具的执行扩展点，但 Agent 尚不能在不访问票务 Web 或持久化层的前提下查询可选日期和场次。用户需要先选择日期和场次，再跳转既有购票页选座。

## What Changes

- A 提供 `queryAvailableDates`、`queryShows` 两个类型化只读 Tool API、Command 与 Result DTO。
- A 在 B 已确认的白名单与装配扩展点登记两个 Adapter；不改 Supervisor、状态机、SSE 编排或确认建单流程。
- 两个 Tool 仅公开参数错误 `100001` 与查询不可用 `306003`；空结果保持成功。

## Non-Goals

- 不注册 `querySeats`，不返回座位明细、锁座结果、订单、支付或用户数据。
- 不新增 REST 路由、迁移、数据库访问、身份解析、写操作、actionId 或幂等键。

## Owners

- A：票务 Tool API、DTO、Adapter、白名单登记和测试。
- B：既有 MultiToolSupervisor、状态机、SSE 编排与重规划，不在本 change 修改。
- C：后续消费 B 映射的 `QUESTION`、`PLAN_CARD`、`SELECT_SEATS` 事件。

## Acceptance

Agent 可从已校验槽位调用两个 Tool；适配器只使用 A 的公开 Application Service；超时预算不扩大；成功、空结果、参数错误和查询不可用均产生约定的 `ToolResult`。
