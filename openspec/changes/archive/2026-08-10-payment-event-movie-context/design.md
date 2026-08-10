# Design

## Boundaries

`ShowContextQueryService` remains the public Application API between ticketing and order. Its internal `ShowContextView` uses `Long movieId` because it is an in-process DTO and movie ID may be absent. `PaymentSucceededEvent` remains the cross-module event and converts a non-null positive `Long` to a decimal `String`.

## Data flow

```text
movie_show.movie_id
  -> ShowQueryRepository.ShowContext
  -> ShowContextQueryService
  -> ShowContextView(Long movieId)
  -> PaymentTransaction / PaidTravelTaskReconciliationService
  -> PaymentSucceededEvent(String movieId or null)
```

The query must validate that the returned `showId` matches the order's show ID. A missing movie ID is preserved as null; no fallback lookup is allowed. The event is published after the payment transaction commits as before.

## D 画像消费者

`ProfileBehaviorRecorder` 保持只消费 A 已提交的 `PaymentSucceededEvent`。当 `movieId` 为非空正十进制字符串，D 通过内容模块的公开 Application API `MovieGenreQueryService` 查询该电影的主类型；内容模块负责读取自己的内容资料和解析 `genresJson`，画像模块不访问内容表、Mapper 或领域对象。

查询到非空主类型时，D 把该支付行为归一化为 `MOVIE_GENRE/<主类型>/LIKE/BEHAVIOR`。`PAID_ORDER=+0.35`，因此第一次有效支付已经达到现有 0.30 阈值并创建或更新标签；相同用户、事件类型、场次在 24 小时内仍只累计一次。`movieId` 缺失、非法、内容不存在、类型为空或内容查询不可用时，只保存 `PAID_ORDER/SHOW` 最小行为摘要，不创建标签，也不影响已完成支付。历史订单不扫描或补采。

## Compatibility and failure handling

- `OrderInvalidated` is unchanged.
- Missing movie ID is non-fatal and does not alter order/payment state.
- Missing show context still follows the existing event compensation behavior; this change only makes a present show context's movie relation nullable.
- D owns movie type lookup, primary genre selection, behavior aggregation and all profile failures.

## Verification

Cover positive mapping, null mapping, real-time payment publication, paid-order reconciliation publication, profile genre creation, unavailable-genre fallback, event replay value preservation and existing travel/refund event behavior. Run OpenSpec strict validation, targeted Maven tests, full backend verification and `git diff --check`.
