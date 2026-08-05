# Design: admin-order-read-model

## 模块边界

`admin/api`只负责参数校验、调用应用服务和映射`Result<PageResult<T>>`。`admin/application`复核ADMIN角色、规范化筛选条件并聚合只读视图。`admin/infrastructure`只查询A拥有的交易表，不提供UPDATE、INSERT或DELETE。

用户信息属于C：A不能在管理SQL中JOIN `sys_user`，也不能复制邮箱脱敏规则。C确认提供以下稳定公开Application API：

```java
package com.miaoyu.ticket.auth.application;

public interface UserAdminQueryPort {
    Set<Long> findUserIdsByKeyword(String userKeyword);
    Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds);

    record UserAdminSummary(long userId, String emailMasked) {}
}
```

该端口由C实现并在入口复核ADMIN。A只调用公开接口，不访问或JOIN`sys_user`，不保存用户索引，也不复制邮箱规范化或脱敏规则。

`findUserIdsByKeyword`契约如下：

- A仅在REST请求明确携带非空用户筛选时调用；参数先`trim`，REST参数存在但为空白时返回`101001/400`。
- 仅包含ASCII数字`[0-9]`时按用户ID精确匹配，允许1至19位；解析为0、超过19位或超过正`long/BIGINT`范围时返回空集合。
- 其他关键字长度为2至100，按C规范化后的邮箱做不区分大小写包含匹配；`%`、`_`、反斜杠按普通字符处理，并使用参数绑定和明确的SQL `ESCAPE`字符。
- C最多读取101个不同用户ID；0至100个时返回全部结果，第101个只用于判断过宽。达到101个时抛出`201010 USER_QUERY_TOO_BROAD/400`，不得返回前100个。
- Repository或数据库不可用时抛出`301002 USER_DIRECTORY_UNAVAILABLE/503`，不得返回空集合或部分结果；日志不记录原始关键字。

`findByUserIds`拒绝`null`集合、`null`元素、非正数和去重后超过100个ID；空集合直接返回空Map且不查询数据库。C使用一次批量查询，仅返回存在的用户，并通过已有`EmailAddress.mask`生成`emailMasked`。A遇到Map缺失用户时保留历史订单并返回`emailMasked=null`；整个端口不可用时返回`301002/503`。

## 权限边界

Application Service每次查询都从`CurrentUserAccessor.requireCurrentUser()`取得身份并要求`role=ADMIN`，否则抛出`CommonErrorCode.FORBIDDEN`（`100403`）。该检查是绕过HTTP入口调用时的纵深防御，不替代C的安全链；C已在唯一安全链限制`/api/v1/admin/**`为ADMIN。真实HTTP请求先经过该安全链，普通用户返回`403/201007`，匿名用户返回`401/201006`。PR #71 已通过真实CSRF、登录和Cookie/JWT链路验证列表与详情；A的应用层测试仅验证纵深防御，不实现JWT或冒充C的认证验收。

## 查询模型

列表条件：

- `orderNo`精确匹配，最长32字符；
- `status`解析为`OrderStatus`枚举，不接受任意数据库值；
- `movieId/showId`为正整数业务ID；
- `dateFrom/dateTo`按Asia/Shanghai解释为左闭右开创建时间区间，跨度最多31天；
- `page`从1开始，`size`范围1至100；
- `userKeyword`存在但trim后为空时非法；纯ASCII数字允许1至19位，其他关键字允许2至100字符，仅交给C公开服务解析为有限用户ID集合。

当`userKeyword`没有匹配用户时直接返回空分页，不执行无界订单查询。排序固定为`ticket_order.create_time DESC, ticket_order.id DESC`，不接受客户端排序列。

## 聚合与脱敏

管理查询新增独立只读Repository，一次分页查询订单主行，然后按本页`orderId`批量查询订单座位、支付、电子票和退款摘要，禁止逐订单N+1。场次上下文按本页`showId`批量读取A的`movie_show`，返回`movieId/cinemaId/startAt`。

列表返回最小摘要：订单、脱敏用户、场次引用、金额、订单状态、关联支付/票/退款状态、版本和更新时间。详情增加座位快照、支付号/时间、票号/状态和退款号/原因/时间，但不返回：

- 完整邮箱或认证内部字段；
- `clientRequestId`、`Idempotency-Key`或参数摘要；
- 电子票`qrPayload`；
- `refund_request.impact_snapshot`或Agent `actionId`；
- 密码、JWT、Cookie、登录日志或模型上下文。

## 只读一致性

两个用例使用`@Transactional(readOnly = true)`，Repository只声明SELECT。列表是查询时快照；交易可能在查询后变化，前端以详情刷新结果和`stateVersion`为准。管理查询不加业务行锁，不改变订单、支付、票、退款或座位版本。

## 错误与空结果

- 普通用户经真实HTTP安全链访问：HTTP 403 / `201007`；
- 匿名用户经真实HTTP安全链访问：HTTP 401 / `201006`；
- 绕过HTTP入口直接调用A应用服务的非ADMIN身份：`100403`；
- 非法参数：HTTP 400 / `100001`；
- 认证用户查询参数非法：HTTP 400 / `101001`；
- 用户关键字匹配超过100人：HTTP 400 / `201010`；
- 详情订单不存在：HTTP 404 / `205001`；
- 列表无记录：HTTP 200，`total=0`且`records=[]`；
- C用户摘要暂不可用：HTTP 503 / `301002`，不使用完整邮箱、空字符串或伪造用户摘要降级。

## 迁移与发布

现有V002、V003、V005和V006已包含查询所需字段与索引。本change不新增、不修改任何Flyway文件。发布顺序为：C确认用户摘要契约；A实现管理查询；C通过PR #71接入正式用户目录端口并完成安全链验收；后端和前端分别联调。

C正式`UserAdminQueryPort` Bean已随PR #71合入；A的`@ConditionalOnMissingBean`失败关闭兜底已自动让位。兜底仅在正式Bean异常缺失的部署故障中返回`301002/503`，不查询`sys_user`、不返回空集合或伪造用户。

## 测试策略

- Application/API集成测试覆盖ADMIN成功、USER 403、非法筛选、空分页和详情404。
- 聚合测试覆盖待支付、已支付和已退款订单，以及支付/票/退款可空关系。
- 脱敏扫描确保响应和日志不包含完整邮箱、二维码载荷、幂等键、impactSnapshot或actionId。
- 查询前后比较交易表状态与版本，证明管理接口严格只读。
- Repository测试验证分页稳定排序、组合筛选、批量聚合和无N+1调用。
- JSON夹具和OpenAPI测试覆盖管理列表、详情、`201010`、`301002`以及敏感字段隔离。
- 按PR审核规范分别统计本PR新增生产代码与admin模块有效注释率，二者均不得低于30%。

## 前端展示层

- **响应式布局**：以 `1024px` 为断点，PC 端显示 Table，移动端显示单列卡片。
- **状态及错误处理**：对各种可能的 HTTP 和业务错误状态使用独立的 `AdminOrderError` 显示安全错误信息；列表通过 `AdminOrderStatus` 统一渲染所有子状态。
- **展示层隔离**：`pages` 只组合状态，`modules/admin` 通过 C 的唯一 `apiRequest` 调用管理订单 GET 接口，`features` 仅渲染展示模型；路由和 `RequireAdmin` 仍由 C 维护。
- **查询恢复**：筛选变化取消旧请求并拒绝迟到响应。`301002` 或网络/5xx 保留筛选条件和已有内存列表并允许手动重试；`201010` 引导收窄条件，403 失败关闭且不展示历史数据。
- **详情查询**：打开抽屉后按订单号查询，切换或关闭时取消旧请求；403/404 不重试，网络和服务异常允许按原订单号手动重查。
