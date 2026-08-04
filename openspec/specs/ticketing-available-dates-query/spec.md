# ticketing-available-dates-query Specification

## Purpose
TBD - created by archiving change ticketing-available-dates-query. Update Purpose after archive.
## Requirements
### Requirement: 公开查询指定影片和影院的可售日期

系统 SHALL 提供公开的 `GET /api/v1/shows/available-dates`。`movieId` 和 `cinemaId` SHALL 为必填的正十进制字符串业务 ID。接口 SHALL 返回 `Result<AvailableDatesResponse>`，其中 `dates` SHALL 仅包含 `date` 和非负整数 `showCount`。

#### Scenario: 按日期聚合未来排期

- GIVEN 指定影片和影院在未来七天内存在多个 `ON_SALE` 且未开场的场次
- WHEN 匿名调用方查询可售日期
- THEN 系统按业务日期升序返回每个有匹配场次的日期
- AND 每项 `showCount` 等于该日期匹配的场次数量
- AND 接口不要求登录身份

#### Scenario: 排除不符合可售日期规则的场次

- GIVEN 数据中同时存在已开场、恰好到达开场时刻、停售、七天窗口外、其他影片和其他影院的场次
- WHEN 查询指定 `movieId + cinemaId` 的可售日期
- THEN 上述场次均不计入日期或 `showCount`
- AND 系统只依据场次状态和时间统计，不因座位余量改变日期结果

#### Scenario: 没有符合条件的日期

- GIVEN 查询参数合法但不存在匹配的未来场次
- WHEN 调用可售日期接口
- THEN 返回 HTTP 200 和 `dates: []`
- AND 不返回 404，不伪造日期或场次

#### Scenario: 缺少或使用非法业务 ID

- GIVEN 请求缺少任一必填参数，或者 ID 不是正十进制字符串、超过 `long` 范围
- WHEN 调用可售日期接口
- THEN 返回 HTTP 400 和错误码 `100001`
- AND 不执行无条件场次查询

### Requirement: 可售日期查询保持只读和模块边界

系统 SHALL 只读取 A 拥有的排期事实并执行有界聚合。该查询 SHALL NOT 写数据库、访问 D 的私有持久化类型、调用 B 的 Agent 运行能力或依赖 C 的认证实现。

#### Scenario: 查询不产生业务副作用

- GIVEN 数据库中存在场次、座位和交易状态
- WHEN 重复执行相同可售日期查询
- THEN 每次返回基于当前权威场次快照的结果
- AND 场次、座位、订单、支付、电子票和退款记录均不被修改

### Requirement: 契约和实现具备回归证据

系统 SHALL 为可售日期查询同步 OpenAPI、REST Mock、应用与 HTTP 测试，并提供可选的真实 MySQL 只读集成验证入口。

#### Scenario: 执行交付质量门

- GIVEN 本 change 的实现已经完成
- WHEN 执行 OpenSpec strict、Maven verify 和差异检查
- THEN 接口、分层、测试、Checkstyle、SpotBugs 和 JaCoCo 全部通过
- AND 未执行的外部环境测试被明确记录，不冒充已验证结果
