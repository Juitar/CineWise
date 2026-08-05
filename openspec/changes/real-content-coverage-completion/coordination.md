## 第一章确认材料

本文件记录 D 已完成的字段核对与跨模块输入，不能替代 A、B、C 本人确认。未收到明确回复的项目保持“待确认”，不会据此修改迁移、公开接口或 Agent 工具。

### 1. 真实资料字段表

| 资料 | Provider 已核验字段 | D 内部字段 | 可空 | Owner | 消费者与处理规则 |
| --- | --- | --- | --- | --- | --- |
| 影片身份 | `id` | `sourceMovieId`、内容身份映射 `externalId` | 否 | D | 同步、A 身份解析；不得按标题猜测 |
| 片名 | `nm` | `title` | 否 | D | REST、C 影片页 |
| 海报 | `img` | `posterUrl` | 是 | D | D 仅保留规范化 HTTPS 绝对地址；C 加载失败显示占位 |
| 类型 | `cat` | `genres` | 否 | D | 本地分页筛选、C 影片页 |
| 时长 | `dur` | `durationMinutes` | 否 | D | REST、C 影片页 |
| 评分 | `sc` | `rating` | 是 | D | Provider 未给出时为 `null`，不补造 |
| 上映日期 | `rt` | `releaseDate` | 是 | D | 本地按日期排序；不代表有可售场次 |
| 上映状态 | `globalReleased`、目录来源 | `releaseStatus` | 是 | D | 区分热映/待映；不推断票务状态 |
| 短简介 | 详情接口待逐字段核验 | `summary` | 是 | D | 只接受短简介；影评正文不保存 |
| 影院身份 | `id` | `sourceCinemaId`、内容身份映射 `externalId` | 否 | D | 同步、A 身份解析 |
| 影院名称/地址 | `info.name`、`info.address` | `name`、`address`、`area` | 否 | D | C 影院页；不以地址推断坐标 |
| 城市 | `cities.json` 的 `id`、`nm`、`py` | `cityName`、内部 `providerCityId` | 否 | D | C/B 只传临时地点字符串或城市名；D 解析唯一城市名，`ci` 不对外暴露 |
| 坐标 | 需 Provider 实际提供 | `longitude`、`latitude` | 是 | D | 本期影院浏览不消费坐标；未来用户主动路线可在页面内存使用，无坐标不显示距离 |

不保存：影评正文、评论、排期、价格、库存、座位、订单、用户位置、Provider 原始响应和密钥。

### 2. C 的接口与页面输入

- 现有前端共享类型已包含可空 `posterUrl`、`summary`，影片页已做 HTTPS/同源二次校验和空海报占位；本次后端可复用该字段名和可空类型。
- `releaseDate`、`releaseStatus` 已由 C 确认纳入影片列表和详情 DTO：前者只接受可空 `YYYY-MM-DD`，后者只允许 `NOW_SHOWING`、`COMING_SOON` 或 `null`。保留 `expiresAt/isExpired` 及既有来源字段，后端 DTO、OpenAPI、Mock 和组合测试必须同步采用同一规则。
- C 以 `POST /api/v1/content/cities/resolve` 向 D 提交 `{locationText}`，避免地点原文进入 URL；响应只含 `status=RESOLVED|UNRECOGNIZED|SELECTION_REQUIRED` 和仅在 `RESOLVED` 时返回的 `cityName`。不返回候选地点、`providerCityId` 或 `ci`。页面只以 `cityName` 查询影院和展示影院 DTO。
- 管理员同步接口继续复用 Cookie + CSRF 规则，后端 `/api/v1/admin/**` 已由安全配置限制 `ADMIN`。`POST /api/v1/admin/content/sync` 的请求只含 `clientRequestId/cityName`；结果查询固定为 `GET /api/v1/admin/content/sync/by-request/{clientRequestId}`。公开响应不返回 `providerCityId`、`ci`、Provider URL、原始异常或原始响应。

### 3. A 的迁移与票务边界输入

- 已形成迁移申请设计：`movie` 新增可空 `poster_url/summary/release_status/release_date`；`content_identity_mapping` 保存外部身份、内部内容 ID、ACTIVE/INVALID 状态、固定失效分类和生成 ACTIVE 唯一键；`cinema` 新增 `city_name/provider_city_id`；`data_sync_log` 新增城市字段和固定 `failure_category`，完整字段、索引、CHECK、180 天清理和兼容规则见 `design.md`。
- 当前已发布迁移最高为 V012。A 已正式分配本 change 使用 V014；D 先补齐并同步完整 OpenSpec，再提交 V014 SQL 草案给 A 静态复核。复核通过前 D 不创建或执行 SQL，不修改既有迁移，也不自行执行 Flyway。
- 迁移还需为 `cinema` 与 `data_sync_log` 增加 `city_name`、`provider_city_id`，使按城市同步、状态展示和按原请求恢复可追溯；地点原文不落库。
- 真实影片/影院仅展示基础资料。没有 A 公开可售结果时，C 显示“暂无可售场次”，不显示价格、余座或购票入口。
- A 未来只能经 D 的公开 Application API，用 `provider + resourceType + externalId` 解析内部 ID；不得读取 D 的 Entity、Mapper、Repository、缓存或内容表。
- A 已确认：城市信息由 D 解析为本地 `cinemaIds`，A 不处理城市信息；A 将在自己的 OpenSpec 中新增按业务日期和 `cinemaIds` 查询可售场次的公开只读 Application API。D 只调用该 API，不访问票务持久层。
- `recommendation_record` 暂不进入本次迁移申请；推荐历史范围和表设计完整后另行决定是否申请新的迁移版本。

### 4. 已定义的批量身份解析规则

- 请求范围：一个 `provider`、一个 `resourceType`（仅 `MOVIE` 或 `CINEMA`）、1 至 100 个 `externalId`。
- 规范化：三个字段去首尾空白后非空；`provider` 最长 64，`externalId` 最长 128；同批重复 ID 只查一次，按首次出现顺序返回。
- 整体参数不合法返回 `100001`；逐项失败不影响同批其他项：无 ACTIVE 映射 `303005`，多条映射 `303006`，旧映射失效 `303007`。
- 内容逻辑删除、来源身份替换或身份冲突时，旧映射标记 `INVALID`；旧外部 ID 不得静默指向新内容。

### 5. B 工具影响核对

`RankMoviePlanTool` 的输入仍是内部 `movieId/cinemaId`，输出只读取 A 的公开场次查询；它不读取 Provider 原始数据，也不接受外部身份、海报、简介、城市定位或资料版本字段。因此 D 的身份解析 API 是给 A 后续排期读取模型使用，不改变 B 的 Tool Schema、SSE、Agent 轨迹或只读边界。

### 6. 待 Owner 回复的最小事项

| Owner | 需要明确回复 | 未回复前的处理 |
| --- | --- | --- |
| C | 已确认城市解析、影院查询和管理员同步接口 | 实现前同步 OpenAPI、Mock 和前端类型 |
| A | 静态复核已补齐的字段、索引、状态 CHECK、清理和兼容设计，以及后续 V014 SQL 草案 | 不创建或执行 SQL；静态复核通过后由 A 明确授权空 MySQL 验证 |
| B | 已确认地点原文持久化前剔除规则 | 等 B 完成 1.5a 的实现与测试 |

## 2026-08-05 B、C 确认结论

### B

B 已确认 `RankMoviePlanTool` 保持只接收内部 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`，不新增 `locationText`；身份解析 API 不得把 `userId`、外部 ID、海报、定位、场次、价格、库存或座位加入 ToolContext、命令或模型槽位。场次、价格、库存继续只来自 A 的公开查询。

B 同时确认：`locationText` 只作为本次 D 城市解析调用的内存参数，调用结束立即丢弃。由于 Agent 会持久化用户消息，B 必须在保存用户消息、事件、槽位快照、长期上下文、日志和缓存前剔除或替换原始地点文本，不能只依赖 DTO 不落库。允许保存 D 返回的城市名或标准 `cityCode`，但不得保存原始地点文本、精确位置、经纬度或 NetStart 内部城市 ID。

### C

- `releaseDate` 为可空 `YYYY-MM-DD` 字符串；`releaseStatus` 只允许 `NOW_SHOWING`、`COMING_SOON` 或 `null`，两个字段同时进入影片列表和详情，不新增上映状态筛选参数。
- 保留 `source`、`sourceType`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`；不得删除或改变 `dataTime` 的现有含义。
- 当前真实版本无论直接读取、Redis 命中还是读取当前快照，均返回 `sourceType=LIVE`、`degraded=false`、`fallbackType=null`；`dataTime` 始终是成功同步时间。`isExpired=true` 只提示资料已过期，不自动表示降级。
- 只有两版本快照实现后，无法读取最新版本而回退到上一份真实版本时才返回 `degraded=true`、`fallbackType=SNAPSHOT`。`CACHE` 仅为兼容保留，不能用于普通 Redis 命中。
- C 已确认城市解析接口为 `POST /api/v1/content/cities/resolve`，请求 `{locationText}`，响应 `{status, cityName}`；只有 `RESOLVED` 返回城市名。地点原文只在当前调用内存中存在；当前页面会话可保留 `cityName`，不得保留原始文本。
- C 已确认影院查询参数与影院 DTO 使用 `cityName`，不再使用 `location=430100` 或公开 `cityCode`；本期不计算距离或距离优先。
- C 已确认管理员同步接口和响应字段：`POST /api/v1/admin/content/sync`、`GET /api/v1/admin/content/sync/by-request/{clientRequestId}`、`GET /api/v1/admin/content/sources`。同步结果只含 `syncId/clientRequestId/cityName/status/startedAt/finishedAt/successCount/failureCount/failureCategory`；来源状态只含 C 已确认字段，不公开 Provider 城市 ID。
- C 已确认两个 GET 不要求 CSRF，POST 必须携带 `X-XSRF-TOKEN`；未登录 `401/201006`、非管理员 `403/201007`、CSRF 无效 `403/201009`、无效城市 `400/100001`、整体不可用 `503/303004`。同一 `clientRequestId + cityName` 返回原任务；同一请求标识但城市不同返回 `409/100409`；原任务不存在返回 `404/100404`。
- C 已确认 POST 超时或断网时页面进入 `RESULT_UNKNOWN`，禁用再次同步并只查询原 `clientRequestId`；重新取得 CSRF Token 后不得自动重发同步请求。

## 2026-08-05 本次方案更新

- NetStart `https://apis.netstart.cn/maoyan/cities.json` 实测返回 1151 条 `id/nm/py`；长沙为 `70`，杭州为 `50`，当前返回城市名无重复。D 将目录作为版本化本地 JSON 随应用发布，用户请求不访问该接口。
- C/B 提供地点字符串、D 解析城市名并查本地目录；该方案不以中国行政区划代码作为 NetStart 转换前置条件。
- A 已确认 #76 的城市解析、`cinemaIds -> A` 批量场次 API 边界及暂不做推荐历史表；并正式分配 V014。先更新 OpenSpec 的四类结构设计和过期版本描述，再提交 V014 SQL 草案给 A 静态复核；静态复核通过后，A 再明确授权使用 `cinewise_migration_check + cinewise_migrator` 验证。
- A 已确认不在本 change 申请推荐历史表；现阶段优先补 D 的批量影院摘要端口与城市到 `cinemaIds` 的规则，等待 A 提供可售场次 API OpenSpec 后再联调。
