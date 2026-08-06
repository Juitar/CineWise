# Proposal: ticketing-canonical-business-id

## 背景

Agent 选座入口需要把 B 的 `businessRef.showId` 与 C 的路由参数交给 A 的票务查询接口。现有 `ShowController` 仅依赖 `Long.parseLong`，会接受前导零形式，不能把已确认的跨模块 canonical ID 规则落实为后端边界。

## 范围

- A 的场次、日期、可售影片与座位图 REST 入口统一仅接受无前导零的正十进制业务 ID。
- 保持既有成功响应、错误码 `100001`、鉴权和领域查询语义不变。
- 补充集成测试与 OpenSpec 验收记录。

## 非范围

- 不修改 B 的 Agent 卡片协议、夹具或工具实现。
- 不修改 C 的前端校验、路由或公共请求层。
- 不修改订单写接口、数据库迁移或 ID 生成策略。

## Owner 与确认

- Owner：A（票务 REST 边界）。
- B 已确认 `SELECT_SEATS.businessRef` 将输出 `showId/movieId/cinemaId` 三个必填正十进制字符串。
- C 已确认按 A 固定路由校验并传递这三个字段。
