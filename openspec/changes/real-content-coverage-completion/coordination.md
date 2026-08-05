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
- C 的手动城市选择和 B 的对话地点输入统一向 D 提供临时 `locationText`；D 返回唯一城市名或不可用/需选择结果。地点原文不得进入页面长期状态、用户偏好、日志或画像。浏览页不计算或展示距离。
- 管理员同步接口继续复用现有 Cookie + CSRF 规则，后端 `/api/v1/admin/**` 已由安全配置限制 `ADMIN`；新请求只包含 `clientRequestId/cityName`，具体路径和响应字段需 C 按本 change 更新确认。

### 3. A 的迁移与票务边界输入

- 影片拟新增可空字段：`poster_url`、`summary`、`release_status`、`release_date`；内容身份映射需要独立保存 `provider`、`resource_type`、`external_id`、内部内容 ID、`status`（`ACTIVE`/`INVALID`）和最小失效原因。
- A 已允许进入本 change 的迁移准备阶段；A 仍需分配最终 Flyway 版本、审查 SQL，并在空 MySQL 验证。D 不修改既有迁移，也不自行执行 Flyway。
- 迁移还需为 `cinema` 与 `data_sync_log` 增加 `city_name`、`provider_city_id`，使按城市同步、状态展示和按原请求恢复可追溯；地点原文不落库。
- 真实影片/影院仅展示基础资料。没有 A 公开可售结果时，C 显示“暂无可售场次”，不显示价格、余座或购票入口。
- A 未来只能经 D 的公开 Application API，用 `provider + resourceType + externalId` 解析内部 ID；不得读取 D 的 Entity、Mapper、Repository、缓存或内容表。
- A 已确认：城市信息由 D 解析为本地 `cinemaIds`，A 不处理城市信息；A 将在自己的 OpenSpec 中新增按业务日期和 `cinemaIds` 查询可售场次的公开只读 Application API。D 只调用该 API，不访问票务持久层。
- `recommendation_record` 暂不进入本次迁移申请；推荐历史范围和表设计完整后再决定是否申请 V013。

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
| C | 城市解析/影院查询新 DTO、手动选城和临时地点输入、管理员按城市同步接口格式 | 不修改公开 REST DTO 和前端类型 |
| A | 影片字段、身份映射、影院/同步日志城市列的最终列和索引、Flyway 版本和空 MySQL 验证窗口 | 不创建或执行迁移 |
| B | 书面确认地点字符串仅临时传给 D、不得进入 Agent 轨迹或长期上下文 | 不修改 B 的 Tool Schema 或 Agent 代码 |

## 2026-08-05 B、C 确认结论

### B

B 已确认本次无需修改 B 侧代码和 Tool Schema。`RankMoviePlanTool` 保持只接收内部 `movieId`、`cinemaId`、`date`、`timeFrom`、`timeTo`；身份解析 API 不得把 `userId`、外部 ID、海报、定位、场次、价格、库存或座位加入 ToolContext、命令或模型槽位。场次、价格、库存继续只来自 A 的公开查询。

### C

- `releaseDate` 为可空 `YYYY-MM-DD` 字符串；`releaseStatus` 只允许 `NOW_SHOWING`、`COMING_SOON` 或 `null`，两个字段同时进入影片列表和详情，不新增上映状态筛选参数。
- 保留 `source`、`sourceType`、`dataTime`、`expiresAt`、`isExpired`、`degraded`、`fallbackType`；不得删除或改变 `dataTime` 的现有含义。
- 当前真实版本无论直接读取、Redis 命中还是读取当前快照，均返回 `sourceType=LIVE`、`degraded=false`、`fallbackType=null`；`dataTime` 始终是成功同步时间。`isExpired=true` 只提示资料已过期，不自动表示降级。
- 只有两版本快照实现后，无法读取最新版本而回退到上一份真实版本时才返回 `degraded=true`、`fallbackType=SNAPSHOT`。`CACHE` 仅为兼容保留，不能用于普通 Redis 命中。
- 原管理员接口和城市编码确认已被本 change 的新方案替换：待 C 确认的同步请求只包含 `clientRequestId/cityName`；状态至少包含城市名、`PENDING/RUNNING/SUCCESS/PARTIAL/FAILED`，地点原文和 `ci` 不对外返回。
- 本期影院浏览默认长沙，支持手动城市和 C/B 已得到的临时地点字符串解析；不计算距离或距离优先。基础路线仍由用户主动触发定位，精确位置不传给内容接口且不持久化。

## 2026-08-05 本次方案更新

- NetStart `https://apis.netstart.cn/maoyan/cities.json` 实测返回 1151 条 `id/nm/py`；长沙为 `70`，杭州为 `50`，当前返回城市名无重复。D 将目录作为版本化本地 JSON 随应用发布，用户请求不访问该接口。
- C/B 提供地点字符串、D 解析城市名并查本地目录；该方案不以中国行政区划代码作为 NetStart 转换前置条件。
- 用户转述 A 已允许进入迁移准备。该记录不替代 A 对最终版本、字段、索引和空 MySQL 验证窗口的书面确认。
- A 已确认不在本 change 申请推荐历史表；现阶段优先补 D 的批量影院摘要端口与城市到 `cinemaIds` 的规则，等待 A 提供可售场次 API OpenSpec 后再联调。
