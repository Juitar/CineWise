# Design: content-and-show-selection-flow

## 模块与依赖方向

票务查询代码位于`com.miaoyu.ticket.ticketing`，继续使用`api/application/domain/infrastructure`分层。Controller只校验HTTP参数并调用Application Service；Application Service组合票务权威查询与内容摘要端口；MyBatis Entity、Mapper和SQL只位于infrastructure。

`movie`和`cinema`由D内容模块拥有。A依据今日任务统一生成其基线迁移和Mock种子，但业务代码不得跨模块访问D的Entity、Mapper或Repository。场次响应所需`cinemaName`通过公开`ContentSummaryQueryPort`取得；D实现未合并前，只允许使用经D确认、显式标记`MOCK/demo-seed`的Demo Adapter。

## 数据库与迁移

迁移按全局Flyway顺序分为内容基础表和票务排期表，不建立物理外键：

1. `movie`、`cinema`：保存内容标准化快照和来源时间。
2. `auditorium`、`movie_show`、`show_seat`：保存影厅、排期和场次座位权威状态。

所有内部主键使用应用分配的BIGINT雪花ID；金额使用`DECIMAL(10,2)`；时间使用`DATETIME(3)`；字符集使用utf8mb4。迁移文件一旦在共享数据库执行不得修改。

## 固定种子

种子不是Flyway结构迁移。种子通过`SEED_ENABLED`控制，使用`SEED_FIXED_VALUE`和注入的`Clock`，以`Asia/Shanghai`运行日为基准生成未来0至6天数据：

- 8至12部Mock影片；
- 3至5家Mock影院，每家2个普通影厅；
- 每个影厅未来7天早、中、晚典型场次；
- 每个场次完整座位图。

幂等键分别为`source+source_movie_id`、`source+source_cinema_id`、`cinema_id+name`、`auditorium_id+start_time`、`show_id+row_no+seat_no`。重复初始化只补缺失对象，不重置已锁定、已售或不可用座位。

## 场次查询

`GET /api/v1/shows`的`movieId`和`cinemaId`必填，`date/timeFrom/timeTo`可选。查询只返回满足以下条件的场次：

- 影片和影院同时匹配；
- `status=ON_SALE`；
- `start_time>Clock.instant()`；
- 日期与时间范围匹配；
- 数据位于未来7天演示窗口。

余座数按查询快照中`show_seat.status=AVAILABLE`聚合。`ShowSummaryResponse.stateVersion`取`movie_show.version`，表示场次元数据版本，不承诺座位仍可锁定。

## 座位图查询

`GET /api/v1/shows/{showId}/seats`要求登录。Application Service重新读取场次并校验存在、`ON_SALE`和未开场，再按排号、座号稳定排序返回座位。

顶层`stateVersion`仍取`movie_show.version`；每个`SeatItemResponse.stateVersion`取对应`show_seat.version`。调用方判断单座位变化时使用座位版本，不能把顶层版本当作全部座位的聚合锁版本。`updatedAt`取本次快照中场次与座位的最新更新时间，只用于判断响应新旧。

## DTO

```text
ShowSummaryResponse(
  showId, movieId, cinemaId, cinemaName,
  auditoriumId, auditoriumName,
  startTime, endTime, languageVersion,
  basePrice, availableSeatCount,
  status, dataType, stateVersion, updatedAt
)

SeatMapResponse(
  showId, auditoriumId, auditoriumName,
  rowCount, seatCount, availableSeatCount,
  stateVersion, updatedAt, seats
)

SeatItemResponse(
  seatId, rowNo, seatNo, seatLabel, status, stateVersion
)
```

HTTP DTO中的ID均为十进制字符串；`basePrice`为两位小数字符串；时间为带偏移ISO 8601；状态值与数据库枚举同名。

## 错误与权限

- 缺少或格式非法的查询参数：HTTP 400 / `100001`。
- 座位接口未登录：HTTP 401 / `100401`。
- 场次不存在：HTTP 404 / `100404`。
- 场次停售或已开场：HTTP 409 / `204002`。
- 无场次是正常结果，返回成功空数组，不伪造候选。

## 测试策略

H2只做快速上下文反馈，不能证明MySQL迁移正确。正式集成测试使用MySQL Testcontainers或独立的MySQL集成测试库，至少验证：空库迁移、种子重复执行、条件场次查询、DTO序列化、登录座位查询、不存在场次和不可售场次。
