# Proposal: content-and-show-selection-flow

## 背景

A 今日需要交付票务只读垂直切片：落地影片、影院、影厅、场次和座位数据库结构，提供可重复初始化的固定演示数据，并完成场次查询与座位图查询。现有后端骨架已经具备统一响应、Flyway、MyBatis-Plus、OpenAPI和默认拒绝安全壳，但尚无领域迁移、种子或票务查询实现。

前端场次页和A票务详设已经冻结`ShowSummary`主体字段；正式后端接口详情仍存在`movieId/cinemaId`可选性的旧描述，座位图响应也缺少完整字段定义。本change先消除契约歧义，再进入实现。

## 目标

- 落地`movie`、`cinema`、`auditorium`、`movie_show`、`show_seat`五张表的向前Flyway迁移。
- 使用固定随机种子和业务唯一键生成可重复的Mock影片、影院、影厅、未来7天场次和完整座位图。
- 实现`GET /api/v1/shows`和`GET /api/v1/shows/{showId}/seats`。
- 冻结REST DTO的ID、金额、时间、状态和版本语义，并与前端契约保持一致。
- 通过真实MySQL集成测试验证迁移、种子、场次查询和座位查询。

## 非目标

- 不实现可用日期、反向影片/影院筛选、场次详情和推荐座位接口。
- 不实现锁座、建单、取消、支付、电子票和退票业务。
- 不实现C负责的JWT、登录注册或SecurityContext解析。
- 不实现D负责的真实外部影片/影院Provider。
- 不把Agent Tool DTO与REST DTO合并为同一个类型。

## Owner与协作

- A：迁移顺序、票务表、固定种子、场次/座位查询、OpenAPI和集成测试。
- C：确认`/shows`公开、`/shows/{showId}/seats`登录访问的安全规则，并提供可用认证上下文。
- D：确认`movie/cinema`字段及`ContentSummaryQueryPort`边界；A不得直接访问D的Repository。
- B：消费票务Application Service时使用独立的Agent Tool DTO，不复用Controller。

## 验收

- 空MySQL 8数据库可执行迁移并生成本change的五张表。
- 固定种子连续运行两次不产生重复业务对象，也不覆盖非`AVAILABLE`座位。
- 固定`movieId+cinemaId`可以查询未来可售场次；合法登录用户可以查询完整座位图。
- 两个接口的响应字段与本change spec一致。
- 至少有场次查询和座位查询的真实MySQL集成测试，并覆盖关键异常。
