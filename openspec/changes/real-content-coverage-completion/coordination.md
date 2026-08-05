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
| 城市 | Provider `ci` 仅作请求参数 | `cityCode` | 否 | D | 系统行政区划代码；首个默认城市长沙 `430100` |
| 坐标 | 需 Provider 实际提供 | `longitude`、`latitude` | 是 | D | C 仅在用户主动距离排序时使用；无坐标不显示距离 |

不保存：影评正文、评论、排期、价格、库存、座位、订单、用户位置、Provider 原始响应和密钥。

### 2. C 的接口与页面输入

- 现有前端共享类型已包含可空 `posterUrl`、`summary`，影片页已做 HTTPS/同源二次校验和空海报占位；本次后端可复用该字段名和可空类型。
- 新增的 `releaseDate`、`releaseStatus`、资料版本、回退原因，以及是否保留/废弃 `expiresAt`、`isExpired`，仍须由 C 明确确认后修改公开 DTO、OpenAPI、Mock 和前端类型。
- 定位只在用户点击“使用当前位置”后请求；位置和距离只保留页面内存。首次无选择时使用长沙 `430100`，拒绝授权后必须可手动选城。
- 管理员同步接口需复用现有 Cookie + CSRF 规则，后端 `/api/v1/admin/**` 已由安全配置限制 `ADMIN`；具体路径、请求标识和结果查询格式待 C 确认。

### 3. A 的迁移与票务边界输入

- 影片拟新增可空字段：`poster_url`、`summary`、`release_status`、`release_date`；内容身份映射需要独立保存 `provider`、`resource_type`、`external_id`、内部内容 ID、`status`（`ACTIVE`/`INVALID`）和最小失效原因。
- A 负责分配新的 Flyway 版本、审查 SQL，并在空 MySQL 验证；D 不修改既有迁移，也不自行执行 Flyway。
- 真实影片/影院仅展示基础资料。没有 A 公开可售结果时，C 显示“暂无可售场次”，不显示价格、余座或购票入口。
- A 未来只能经 D 的公开 Application API，用 `provider + resourceType + externalId` 解析内部 ID；不得读取 D 的 Entity、Mapper、Repository、缓存或内容表。

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
| C | `releaseDate/releaseStatus`、版本/回退字段、`expiresAt/isExpired` 兼容方案、管理员同步接口格式 | 不修改公开 REST DTO 和前端类型 |
| A | 影片字段与身份映射表的最终列、索引、生命周期、Flyway 版本和空 MySQL 验证窗口 | 不创建或执行迁移 |
| B | 书面确认本节第 5 条的无 Tool Schema 影响结论 | 不修改 B 代码或 Tool Schema |

## 2026-08-05 B、C 确认结论

### B

B 已确认本次无需修改 B 侧代码和 Tool Schema。`RankMoviePlanTool` 保持只接收内部 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`；身份解析 API 不得把 `userId`、外部 ID、海报、定位、场次、价格、库存或座位加入 ToolContext、命令或模型槽位。场次、价格、库存继续只来自 A 的公开查询。

### C

- `releaseDate` 为可空 `YYYY-MM-DD` 字符串；`releaseStatus` 只允许 `NOW_SHOWING`、`COMING_SOON` 或 `null`，两个字段同时进入影片列表和详情，不新增上映状态筛选参数。
- 保留 `source`、`sourceType`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`；不得删除或改变 `dataTime` 的现有含义。
- C 要求 Redis 缓存继续返回 `degraded=true`、`fallbackType=CACHE`；旧真实快照为 `SNAPSHOT`。这与用户提出“缓存中的最新真实资料不应展示为降级”的要求冲突，未据此修改实现，等待用户决定。
- 管理同步接口固定为：`GET /api/v1/admin/content/sources`、`POST /api/v1/admin/content/sync`、`GET /api/v1/admin/content/sync/by-request/{clientRequestId}`。同步请求包含 `clientRequestId/provider/cityCode/resourceType`；状态只允许 `PENDING/RUNNING/SUCCESS/PARTIAL/FAILED`。
- 本期影院浏览只做默认长沙 `430100` 和手动选城；不申请定位、不实现距离优先。基础路线仍由用户主动触发定位，位置不传给内容接口且不持久化。
