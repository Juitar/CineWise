# 设计：影片可售影院入口

## 接口与范围

后端已提供匿名公开只读接口 `GET /api/v1/shows/available-cinemas?movieId=&page=&size=`，安全白名单已在唯一 `applicationSecurityFilterChain` 中放行精确 GET 路径。

`movieId` 只接受无前导零的正十进制字符串；默认 `page=1`、`size=20`，`size` 取值为 1 至 50。成功返回 `PageResult<AvailableCinemaResponse>`，每项包含：`cinemaId`、`name`、`address`、`availableShowCount`、`nearestStartTime`、`contentSource`、`contentDataTime`、`contentExpiresAt`、`contentExpired`、`scheduleSource`、`scheduleDataTime`。

合法无可售影院返回 HTTP 200 和空 `records`；参数非法为 HTTP 400 / `100001`；影片不存在或下线为 HTTP 404；内容摘要不可用为 HTTP 503 / `303004`；票务查询不可用为 HTTP 503 / `306003`。接口、OpenAPI 和固定夹具由 A 提供，前端只消费公开 DTO，不推断可售条件、价格、库存或座位。

本 change 只覆盖影片列表到可售影院页，再跳转既有场次页。不修改 `/shows`、选座、建单、支付、电子票和退票。

## 前端分层

`modules/ticketing` 增加 DTO、API 和 `useAvailableCinemas(movieId)` Hook。API 使用公共 `apiRequest<T>()`；Hook 处理取消、竞态、加载、失败、重试和当前页面生命周期内的只读快照。页面不直接请求网络。

`/movies` 中每张影片卡片通过语义化 `Link` 进入 `/movies/:movieId/cinemas`。选影院页只从路由取得字符串 `movieId`，并用该 Hook 渲染影院；选择影院只生成 `/shows?movieId={movieId}&cinemaId={cinemaId}`。

## 页面状态

页面独立显示加载、合法空结果、失败与重试、404、内容或票务不可用、离线内存快照、内容过期、演示来源和降级来源。失败不能伪装为空结果；空结果不展示购票入口。`contentExpired` 或来源为演示/降级时明确提示数据不是实时可售承诺。

同一套 DTO、API、Hook 和页面同时服务 PC、移动端与键盘。所有入口、返回与重试均可获得焦点，触控目标最小 44px；窄屏不产生影响操作的横向溢出。

## 测试与回退

测试覆盖影片入口、正常列表和跳转参数、空态、加载、失败重试、404、`303004`、`306003`、过期或降级来源、移动端和键盘访问。回退时移除本 change 的前端页面、路由、模块调用和测试；不改 A 的接口和票务主流程。
