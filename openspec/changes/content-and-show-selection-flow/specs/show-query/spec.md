# Show Query Spec

## ADDED Requirements

### Requirement: 内容与票务表基线

系统 SHALL 通过版本化Flyway迁移创建`movie`、`cinema`、`auditorium`、`movie_show`和`show_seat`。跨模块关联 SHALL 只保存业务ID，不建立物理外键。

#### Scenario: 空MySQL初始化

- GIVEN 一个空的MySQL 8数据库
- WHEN 启用Flyway并执行本change迁移
- THEN 五张表、主键、唯一约束、查询索引和CHECK约束全部创建成功
- AND Flyway记录已执行版本

### Requirement: 固定Mock种子可重复初始化

系统 SHALL 使用固定随机种子和注入Clock生成8至12部影片、3至5家影院、每家2个影厅、未来7天早中晚场次和完整座位图。重复执行 SHALL 不产生重复业务对象，不覆盖交易状态。

#### Scenario: 连续执行两次种子

- GIVEN 首次种子初始化已经成功
- WHEN 使用相同`SEED_FIXED_VALUE`再次初始化
- THEN 影片、影院、影厅、同一影厅同一开场时间的场次和同一场次同一座位数量不增加
- AND 非`AVAILABLE`座位的状态、锁单号、锁过期时间和版本不被重置

### Requirement: 按影片和影院查询场次

系统 SHALL 提供`GET /api/v1/shows`。REST场次页的`movieId`和`cinemaId` SHALL 必填，`date/timeFrom/timeTo` MAY 提供。接口 SHALL 返回`Result<List<ShowSummaryResponse>>`。

`ShowSummaryResponse` SHALL 仅包含并完整包含：`showId/movieId/cinemaId/cinemaName/auditoriumId/auditoriumName/startTime/endTime/languageVersion/basePrice/availableSeatCount/status/dataType/stateVersion/updatedAt`。

#### Scenario: 查询未来可售场次

- GIVEN 固定影片和影院存在未来、已开场、停售及其他影院的混合排期
- WHEN 调用方使用固定`movieId+cinemaId`查询
- THEN 只返回匹配影片和影院、`ON_SALE`、未开场且处于未来7天窗口的场次
- AND `availableSeatCount`等于查询快照中`AVAILABLE`座位数量
- AND ID为字符串、`basePrice`为两位小数字符串、时间为带偏移ISO 8601

#### Scenario: 缺少必填条件

- GIVEN 请求缺少`movieId`或`cinemaId`
- WHEN 调用场次查询
- THEN 返回HTTP 400和错误码`100001`
- AND 不执行无条件全表场次查询

#### Scenario: 没有符合条件的排期

- GIVEN 影片和影院合法但不存在符合条件的未来可售场次
- WHEN 调用场次查询
- THEN 返回成功和空数组
- AND 不伪造场次或内容摘要

### Requirement: 登录后查询权威座位图

系统 SHALL 提供`GET /api/v1/shows/{showId}/seats`，并要求有效登录身份。接口 SHALL 返回`Result<SeatMapResponse>`。

`SeatMapResponse` SHALL 包含`showId/auditoriumId/auditoriumName/rowCount/seatCount/availableSeatCount/stateVersion/updatedAt/seats`。每个座位 SHALL 包含`seatId/rowNo/seatNo/seatLabel/status/stateVersion`。

#### Scenario: 查询合法场次座位图

- GIVEN 用户已登录且场次存在、`ON_SALE`并未开场
- WHEN 用户查询座位图
- THEN 返回该场次全部座位并按`rowNo/seatNo`稳定排序
- AND 顶层`stateVersion`等于场次版本，每个座位版本等于对应座位行版本
- AND `availableSeatCount`只统计`AVAILABLE`座位

#### Scenario: 未登录查询座位图

- GIVEN 调用方没有有效登录身份
- WHEN 调用座位图接口
- THEN 返回HTTP 401和错误码`100401`
- AND 不返回任何座位状态

#### Scenario: 场次不存在或不可售

- GIVEN 场次不存在、已停售或已经开场
- WHEN 已登录用户查询座位图
- THEN 不存在返回HTTP 404和`100404`
- AND 已停售或已开场返回HTTP 409和`204002`
- AND 不把历史座位快照作为当前可选座位返回

### Requirement: 真实MySQL集成测试

场次查询和座位查询 SHALL 各有至少一个真实MySQL集成测试，迁移与种子 SHALL 覆盖空库和重复执行。H2测试 SHALL NOT 被用作MySQL DDL兼容性的唯一验收证据。

#### Scenario: 在真实MySQL执行查询验收

- GIVEN 空MySQL 8数据库已完成迁移和固定种子初始化
- WHEN 执行场次查询与座位查询集成测试
- THEN 两类查询均通过并返回冻结DTO
- AND 测试证据包含实际MySQL版本、迁移版本和测试结果
