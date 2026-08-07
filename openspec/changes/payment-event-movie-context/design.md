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

## Compatibility and failure handling

- `OrderInvalidated` is unchanged.
- Missing movie ID is non-fatal and does not alter order/payment state.
- Missing show context still follows the existing event compensation behavior; this change only makes a present show context's movie relation nullable.
- D owns movie type lookup, primary genre selection, behavior aggregation and all profile failures.

## Verification

Cover positive mapping, null mapping, real-time payment publication, paid-order reconciliation publication, event replay value preservation and existing travel/refund event behavior. Run OpenSpec strict validation, targeted Maven tests, full backend verification and `git diff --check`.
