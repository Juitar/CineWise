# Tasks: content-and-show-selection-flow

## 契约与协作

- [x] 建立proposal、design、show-query spec和可执行任务清单。
- [x] 冻结REST场次查询`movieId/cinemaId`必填规则与`ShowSummaryResponse`字段。
- [x] 冻结REST座位图、逐座位版本、错误码和权限语义。
- [x] C已于2026-08-02确认`/shows`公开、`/shows/{showId}/seats`登录访问，以及ShowSummary、SeatMap和Seat字段契约。
- [ ] C合并JWT Cookie、登录接口、测试登录入口和可用认证上下文；A不得自行伪造JWT。
- [x] D已于2026-08-02确认`movie/cinema`字段、可空外部ID、迁移协作、`ContentSummaryQueryPort`和Demo Adapter边界。
- [x] A负责种子整体编排；`movie/cinema`写入留在D拥有的content模块边界，ticketing不得访问D的Mapper或Repository。

## 数据库与种子

- [x] A生成`movie/cinema`向前迁移，静态核对字段、索引和约束。
- [x] A生成`auditorium/movie_show/show_seat`向前迁移，静态核对字段、索引和约束。
- [x] V001-V003已在本地MySQL 8.0.40可丢弃隔离库完成兼容性预演，确认SQL可执行。
- [x] A按迁移规范在云端MySQL 8.4空库正式执行Flyway并保存验证证据。
- [ ] A实现固定种子初始化器，使用`SEED_FIXED_VALUE`、注入Clock和业务唯一键。
- [ ] A验证种子连续执行两次不重复且不覆盖非`AVAILABLE`座位。

## 查询实现

- [ ] A实现ticketing领域枚举、查询模型和公开内容摘要端口依赖。
- [ ] A实现MyBatis Entity、Mapper、明确字段SQL和Repository适配器，禁止`SELECT *`。
- [ ] A实现ShowQueryService与SeatQueryService。
- [ ] A实现ShowController、Bean Validation、OpenAPI和统一错误映射。
- [ ] C将安全规则接入现有唯一SecurityFilterChain，不新增竞争过滤链。

## 验证与交付

- [ ] 增加场次查询真实MySQL集成测试，覆盖筛选、空结果和DTO字段类型。
- [ ] 增加座位查询真实MySQL集成测试，覆盖成功、401、404和不可售场次。
- [ ] 导出`/v3/api-docs`并由A/C核对前端DTO。
- [ ] 执行`mvnw.cmd clean verify`并记录结果。
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
- 未验证项：固定种子初始化和两个只读查询接口尚未实现及验收。
