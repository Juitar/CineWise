# 设计：影片可售影院入口

## 正式接口

后端已提供匿名公开只读接口 `GET /api/v1/shows/available-cinemas?movieId={movieId}&page={page}&size={size}`，安全白名单已在唯一 `applicationSecurityFilterChain` 中放行精确 GET 路径。`movieId` 为无前导零正十进制字符串；`page` 默认 1，`size` 默认 20，最大 50。

接口返回 `Result<PageResult<AvailableCinemaResponse>>`。每项包含 `cinemaId`、影院摘要、`availableShowCount`、`nearestStartTime`、`contentSource/contentDataTime/contentExpiresAt/contentExpired` 与 `scheduleSource/scheduleDataTime`。可售条件、排序和时间窗口由 A 维护；前端不推断价格、库存、座位或可售规则。

合法未知、下线或暂无排期的影片均返回 HTTP 200、`code=0` 和空 `records`，本接口不区分影片不存在和无可售影院。非法参数为 `100001/400`，内容不可用为 `303004/503`，票务查询不可用为 `306003/503`，不得伪装为空结果。

## 前端分层和路由

`modules/content` 提供 `useMovieDetail(movieId)`，`modules/ticketing` 提供 DTO、API 与 `useAvailableCinemas(movieId)` Hook，API 均复用公共 `apiRequest<T>()`。两个 Hook 分别处理取消、竞态、加载、失败和重试；可售影院 Hook 额外保留当前页面内存快照。页面不直接请求网络。

首页和影片列表卡片通过语义化 `Link` 进入既有 `/movies/:movieId` 路由；首页影院卡片通过语义化 `Link` 进入 `/cinemas/:cinemaId`。页面从路由读取字符串 `movieId`，选择影院只生成 `/shows?movieId={movieId}&cinemaId={cinemaId}`。统计和最近开场时间仅用于影片详情/选影院页，首页影院卡片不展示购票、价格、余座或场次时间；场次页仍重新查询权威场次。

## 页面状态与验收

页面分别显示影片详情和可售影院的加载、404、合法空页、失败重试、内容或票务不可用、来源时间、`contentExpired`、非实时来源和离线内存快照。影片详情 404 显示资源不存在；可售影院合法空页显示“当前没有可售影院”且不显示购票入口；`contentExpired=true` 仅提示影院资料过期，仍保留进入场次页的入口。可售影院接口不会把未知或下线影片返回为 404；若发生非契约 404，按失败状态提供重试。

PC、移动端和键盘共用同一实现，入口与重试可获得焦点，触控目标最小 44px，窄屏不产生影响操作的横向溢出。测试覆盖入口、正常跳转、空态、加载、失败重试、非契约 404、`303004`、`306003`、过期/非实时来源、离线、移动和键盘。

本 change 不修改 `/shows`、选座、建单、支付、电子票、退票或 A 的后端接口。真实 HTTP 联调由 C、A 在可用环境中完成。
