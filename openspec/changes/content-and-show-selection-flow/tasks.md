# Tasks: content-and-show-selection-flow

## 契约与协作

- [x] 建立proposal、design、show-query spec和可执行任务清单。
- [x] 冻结REST场次查询`movieId/cinemaId`必填规则与`ShowSummaryResponse`字段。
- [x] 冻结REST座位图、逐座位版本、错误码和权限语义。
- [ ] C确认`/shows`公开、`/shows/{showId}/seats`登录访问，并合并可用认证上下文。
- [ ] D确认`movie/cinema`字段和`ContentSummaryQueryPort`，或书面确认Demo Adapter边界。

## 数据库与种子

- [x] A生成`movie/cinema`向前迁移，静态核对字段、索引和约束。
- [x] A生成`auditorium/movie_show/show_seat`向前迁移，静态核对字段、索引和约束。
- [ ] A在空MySQL 8数据库执行迁移；本任务必须由A手动确认后才能勾选。
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
