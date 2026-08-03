# Tasks: content-and-show-selection-flow

## 契约与协作

- [x] 建立proposal、design、show-query spec和可执行任务清单。
- [x] 冻结REST场次查询`movieId/cinemaId`必填规则与`ShowSummaryResponse`字段。
- [x] 冻结REST座位图、逐座位版本、错误码和权限语义。
- [x] C已于2026-08-02确认`/shows`公开、`/shows/{showId}/seats`登录访问，以及ShowSummary、SeatMap和Seat字段契约。
- [ ] C合并JWT Cookie、登录接口、测试登录入口和可用认证上下文；A不得自行伪造JWT。
- [x] D已于2026-08-02确认`movie/cinema`字段、可空外部ID、迁移协作、`ContentSummaryQueryPort`和Demo Adapter边界。
- [x] A负责种子整体编排；`movie/cinema`写入留在D拥有的content模块边界，ticketing不得访问D的Mapper或Repository。
- [x] A与D于2026-08-03确认公开场次查询继续使用`basePrice`，并新增`expiresAt=startTime`供D排除已开场可购候选；D不复制场次、价格或库存事实。

## 数据库与种子

- [x] A生成`movie/cinema`向前迁移，静态核对字段、索引和约束。
- [x] A生成`auditorium/movie_show/show_seat`向前迁移，静态核对字段、索引和约束。
- [x] V001-V003已在本地MySQL 8.0.40可丢弃隔离库完成兼容性预演，确认SQL可执行。
- [x] A按迁移规范在云端MySQL 8.4空库正式执行Flyway并保存验证证据。
- [x] A实现固定种子初始化器，使用`SEED_FIXED_VALUE`、注入Clock和业务唯一键。
- [x] A验证种子连续执行两次不重复且不覆盖非`AVAILABLE`座位。

## 查询实现

- [x] A实现ticketing领域错误码、查询模型和公开内容摘要端口依赖。
- [x] A实现MyBatis持久化投影、Mapper、明确字段SQL和Repository适配器，禁止`SELECT *`。
- [x] A实现ShowQueryService与SeatQueryService。
- [x] A实现ShowController、参数校验、OpenAPI和统一错误映射。
- [x] A为`ShowSummaryView`、REST响应和OpenAPI补充`expiresAt`，并保持`expiresAt=startTime`。
- [ ] C将安全规则接入现有唯一SecurityFilterChain，不新增竞争过滤链。

## 验证与交付

- [x] 增加场次查询H2接口契约测试和真实MySQL 8.4只读集成测试，覆盖筛选、空结果和字段类型。
- [x] 增加座位查询H2接口契约测试和真实MySQL 8.4只读集成测试，覆盖成功、401、404和不可售场次。
- [x] 导出`/v3/api-docs`并由A/C核对前端DTO；C于2026-08-02确认权限、ID、金额和时间字段无异议。
- [x] 增加`expiresAt`接口契约、OpenAPI和真实MySQL可选回归断言，验证其等于`startTime`。
- [x] 执行`mvnw.cmd clean verify`并记录结果。
- [ ] 执行本地一键初始化和登录后查询验收，记录命令、响应和未验证事项。

## 迁移验证记录

- 验证日期：2026-08-02。
- 定位：仅用于在接触云端测试库前验证SQL可执行性，不构成迁移规范要求的正式MySQL 8.4验证。
- 环境：Windows本地MySQL 8.0.40，可丢弃隔离库`cinewise_migration_check`；未连接云数据库。
- 执行结果：V001、V002、V003预演均成功，`flyway_schema_history`存在3条成功版本记录。
- 结构结果：10张业务表、10个主键、8个CHECK约束、17个非主键唯一索引；无物理外键、无自增列。
- 类型结果：金额字段均为`DECIMAL(10,2)`，`DATETIME`字段均为毫秒精度，业务表均为InnoDB和utf8mb4。
- 重复执行：再次启动Flyway后仍为3条成功版本记录和11张总表（含Flyway历史表），未重复建表。
- 数据结果：本次只验证结构迁移，未执行固定种子初始化。
- 清理结果：预演数据库、专用账号和`.env.migration-check`均已删除。
- 正式环境：阿里云ECS上的MySQL 8.4.11空库`cinewise`，使用Flyway 13.0.0执行。
- 迁移前检查：连接、目标库、账号、`info`和迁移前`validate`均通过，仅V001-V003处于Pending状态。
- 正式执行：V001、V002、V003全部成功，Schema版本为V003，未导入固定种子或其他测试数据。
- 迁移后检查：3条版本记录全部成功；10张业务表、10个主键、8个CHECK约束、17个非主键唯一索引。
- 数据规则：无物理外键和自增列；金额、时间精度、InnoDB及utf8mb4规则检查通过。
- 重复执行：迁移后`validate`通过；第二次`migrate`返回Schema已是最新且无需迁移。

## 固定种子验证记录

- 实现范围：10部Mock影片、4家Mock影院、每家2个影厅、相对运行日期0至6天的早中晚场次和每场80座。
- 模块边界：影片/影院写入位于content模块；影厅、场次和座位写入位于ticketing模块；job只负责编排公开应用服务。
- 幂等规则：使用已冻结的五组业务唯一键；持久化适配器只查询和插入缺失行，不提供重置座位状态的UPDATE。
- 自动化验证：固定Clock下连续初始化两次，数量保持10/4/8/168/13440；预置`LOCKED`座位的状态、锁单号和版本保持不变。
- 质量门禁：固定种子阶段执行`mvnw.cmd clean verify`通过，当时共9个测试；Checkstyle和SpotBugs均为0问题。
- 云端验证：MySQL 8.4.11连续执行两次后数量仍为10/4/8/168/13440，重复场次和重复座位均为0。
- 安全结果：云端订单数保持0，Flyway历史仍为V001-V003三条；本次没有生成账号、订单、支付或其他模块测试数据。

## 场次与座位查询验证记录

- 接口：实现公开`GET /api/v1/shows`和登录态`GET /api/v1/shows/{showId}/seats`，复用唯一SecurityFilterChain。
- 契约：ShowSummary和SeatMap字段与冻结DTO一致；ID为十进制字符串、金额为两位小数字符串、时间带`+08:00`偏移。
- 数据边界：ticketing只查询排期、影厅和座位；影院名称通过`ContentSummaryQueryPort`批量获取，回退适配器会在D正式实现存在时自动让位。
- H2接口测试：覆盖日期/时段筛选、空数组、缺参400、匿名座位401、登录成功、场次404和停售/已开场204002。
- 云端验证：MySQL 8.4.11只读集成测试通过，真实查询返回匹配场次和80座完整座位图；Flyway和种子均关闭。
- OpenAPI：A已从本地H2测试配置实际导出OpenAPI 3.1文档至`backend/target/openapi.json`；两个GET路径、字符串ID、两位小数字符串金额和时间字段类型核对通过，场次接口无安全要求，座位接口已声明`cookieAuth`；C于2026-08-02确认前端DTO无异议。
- 质量门禁：2026-08-02最后一次执行`mvnw.cmd clean verify`通过，共15个测试、0失败、0错误、1个云端MySQL可选测试跳过；Checkstyle和SpotBugs均通过。
- 未验证项：C的JWT Cookie和测试登录入口尚未合并，因此云端HTTP仅能验收公开场次；登录座位正向链路由测试认证上下文完成。

## 场次候选失效时间验证记录

- 契约：公开Application DTO和REST响应均新增`expiresAt`，固定等于`startTime`；D继续消费`basePrice`。
- H2接口测试：实际响应中的`expiresAt`与固定场次`startTime`一致，OpenAPI声明该字段为`date-time`。
- 真实MySQL测试：2026-08-03使用仓库日常应用配置连接MySQL 8.4，只读执行`ShowQueryMySqlIntegrationTest`；1个测试通过、0失败、0错误，`expiresAt=startTime`、未来场次筛选和80座座位图断言全部通过。Flyway和种子均关闭。
- Redis连通性：本机Redis服务运行中，使用仓库配置鉴权执行`PING`返回`PONG`；场次查询不依赖Redis，因此未为本字段变更新增Redis业务测试。
- 质量门禁：2026-08-03同步最新`dev`后执行`mvnw.cmd verify`通过，Checkstyle和SpotBugs均为0问题；随后在一次性MySQL 8.4隔离库执行`ShowQueryMySqlIntegrationTest`，1个测试通过、0失败、0错误、0跳过。
- OpenSpec：`openspec validate content-and-show-selection-flow --strict`通过。
