# Proposal: alternative-show-navigation-contract

## 背景

本人订单的替代场次接口已经返回场次、影院、开场时间、票价和余座，但没有返回影片 ID。A 负责的购票前端需要以 `showId + movieId + cinemaId` 恢复选座页上下文；若只依赖上一页内存，刷新或直接访问时无法重新取得完整场次摘要。

## 目标

- 为 `GET /api/v1/orders/{orderNo}/alternative-shows` 的每个候选增加十进制字符串 `movieId`。
- 使用退票应用服务已经校验的原场次影片 ID，不增加内容模块查询或第二份影片事实。
- 同步 Application View、REST DTO、OpenAPI、C 消费夹具和自动化测试。
- 保持接口路径、鉴权、筛选、稳定排序、票价、余座和空结果语义不变。

## 非目标

- 不修改数据库结构、Flyway、Mapper SQL 或订单/场次状态机。
- 不返回影片标题、海报、影院地址等 D 拥有的内容字段。
- 不实现前端页面、路由守卫或选座交互。
- 不改变替代场次的可售判断、日期范围或退票事务。

## Owner 与协作

- A 拥有替代场次查询及本次 REST 契约增量。
- A 的前端 change `ticketing-purchase-frontend` 在本变更合入后消费新增字段。
- D 的内容数据和 C 的公共前端壳层不受本次实现影响。
