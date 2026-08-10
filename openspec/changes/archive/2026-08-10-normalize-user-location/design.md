## Context

现有 `BasicRouteCommand`、`BasicRouteProvider` 和 `AmapRouteProvider` 把字符串起点及 `cinemaArea` 用作路线参数；`WeatherQueryService` 与 `FoodSearchService` 以区域文本作为缓存和 Provider 输入；`DistanceContextService` 单独校验浏览器经纬度。详见 proposal.md。

## Goals / Non-Goals

**Goals:**

- 用独立 `geo` 包消除 `content`、`recommendation` 与 `travel` 之间的反向依赖。
- 将用户位置、影院地点、Provider 私有标识分开，保留一次性位置与缓存隐私边界。
- 用单元、集成和外部请求验证覆盖坐标校验、歧义、回退、超时与泄露扫描。

**Non-Goals:**

- 不新增地点收藏、持续定位、地图渲染、Agent 自然语言理解或数据库表。
- 不修改 A 的影院表、票务查询或迁移；不让 B 保存坐标，不让 C 以外的模块处理浏览器授权。

## Decisions

### 1. D 新建独立 geo 包并使用 WGS-84 数值坐标

新增 `ResolvedGeoPoint`、`LocationGranularity`、`UserLocationAdapter` 和 `PlaceGeocodingPort`。内部基准选择 WGS-84，浏览器输入在进入系统时转换；仅高德 HTTP Adapter 在官方文档确认后负责转换为其要求的坐标系。相比在 `common` 放通用 Location，独立 D 包避免 A 获得不必要的地图实现依赖；相比让 travel 或 recommendation 互相依赖，避免循环依赖。

### 2. 用户位置与影院位置使用不同查询入口

浏览器和 B 的 `placeText` 走 `UserLocationAdapter`；影院只经 `CinemaLocationQueryService(cinemaId)` 返回已确认坐标。不得把影院地址或用户输入互相替代。`CinemaLocationQueryService` 只依赖 D 的内容公开 Application API，不读取 Mapper 或 Entity。

位置查询只接受 `source.type=LIVE` 的影院资料。真实缓存和真实快照仍保留 `LIVE` 类型，可以继续使用；通用内容查询回退到 Demo 时，即使 Demo 资料带有坐标或区域，也必须返回空结果，不能提交给路线、天气或餐饮 Provider。内容目录整体不可用时保留内容模块的 `303004`，不得把故障伪装成空结果。

### 3. Provider 只接收其所需的私有参数

`WeatherAdcodeAdapter` 从影院坐标取 `adcode`；`AmapRouteAdapter` 从两个统一坐标组装 `lng,lat`。`adcode` 与字符串坐标不跨出 Infrastructure。天气缓存键为 `adcode + 数据类型`，餐饮缓存键为影院坐标的规范化值和半径，不保存用户坐标。

### 4. 公开契约先由 B/C 确认，再删除旧字段

要替换的公开字段是：C 的距离上传请求与路线请求 `originValue`，B 的 `GetWeatherTool` 输入和地点歧义返回。确认记录必须写入本 change 后，才实现 DTO、OpenAPI、Mock、前端类型、Tool Schema 的破坏性修改。作为过渡，旧 `cinemaArea -> adcode` 仅在逆地理失败时使用，并在相应测试和配置中显式标记 `fallbackType`。

## Risks / Trade-offs

- [高德 Web 服务要求的坐标系或地理编码响应字段尚未由官方资料确认] → 在 Amap Adapter 落地前查询官方文档并记录请求/失败证据；Context7 未提供高德官方库条目，不能以训练记忆代替。
- [B/C 未确认公开字段替换] → 不实施破坏性 DTO、OpenAPI、Tool Schema 或跨模块调用；在任务中保留为前置项。
- [影院真实数据可能缺坐标或坐标无效] → 保留 `null` 并返回不可用，禁止地址猜测和 `0,0` 占位。
- [缓存键包含过度精确位置] → 用户位置不进缓存；影院位置仅取 Provider 查询所需的规范化坐标，缓存中不记录用户输入。

## Migration Plan

1. B/C 在此 change 记录确认字段表、兼容策略和各自验证责任。
2. 实现 geo 类型、坐标校验和影院位置查询；先跑单元测试。
3. 替换路线、天气、餐饮、距离与 NetStart 的 Provider 适配；更新公开 DTO、Tool Schema、OpenAPI、Mock 和夹具。
4. 用受控 Key 做高德和 NetStart 真请求，记录成功/失败返回但不记录 Key 或精确用户坐标。
5. 失败时关闭真实 Provider；保留 Demo 和明确降级，不需要数据迁移或回滚数据库。

## Open Questions

无。B/C 对公开接口字段的确认是实现前置条件，未确认前不进入编码任务。
