# payment-event-movie-context Specification

## Purpose
TBD - created by archiving change payment-event-movie-context. Update Purpose after archive.
## Requirements
### Requirement: A must publish authoritative movie context

A SHALL obtain `movieId` from the `movie_show` row associated with the same `showId` through the public ticketing Application API. A MUST NOT infer it from the show ID, movie name, or content data.

#### Scenario: Valid movie context

- **GIVEN** the authoritative show row has a positive movie ID for the requested show
- **WHEN** A constructs a payment success event
- **THEN** `movieId` is serialized as the matching positive decimal string
- **AND** the event's `showId` and `movieId` refer to the same show row

#### Scenario: Missing movie context

- **GIVEN** the authoritative show row has no movie ID
- **WHEN** A completes payment and constructs a payment success event
- **THEN** payment remains successful
- **AND** the event is still published with `movieId=null`

### Requirement: Real-time and reconciliation events share semantics

The payment transaction publisher and paid-order reconciliation publisher SHALL use the same `ShowContextView` mapping and the same nullable `movieId` semantics. Replaying an event SHALL preserve its movie ID value and SHALL NOT create a new inferred value.

#### Scenario: Reconciliation preserves movie context

- **GIVEN** a PAID order is selected for compensation
- **WHEN** A publishes its `PaymentSucceededEvent`
- **THEN** the event uses the authoritative current show context
- **AND** its movie ID conversion is identical to the real-time payment path

### Requirement: D generates a movie-genre tag from a valid payment movie context

D SHALL use a non-null positive `movieId` from `PaymentSucceededEvent` only through the content module's public Application API to obtain the movie's primary genre. When the genre is available, D SHALL record `PAID_ORDER` as `SHOW` evidence and apply the existing `PAID_ORDER` behavior weight to an `ACTIVE` `MOVIE_GENRE` tag with source `BEHAVIOR` and polarity `LIKE`. D SHALL NOT access content persistence directly, alter the payment result, or backfill historical paid orders.

#### Scenario: A paid movie has a primary genre

- **GIVEN** A publishes a committed payment event with a positive `movieId`
- **AND** the content Application API returns the primary genre `科幻`
- **WHEN** D receives the event after payment commit
- **THEN** D records one `PAID_ORDER/SHOW` behavior event
- **AND** D creates or updates the user's `MOVIE_GENRE=科幻` `BEHAVIOR` tag using the existing weight and expiry rules

#### Scenario: Movie context cannot yield a genre

- **GIVEN** the payment event has no valid `movieId`, or the content Application API has no usable genre
- **WHEN** D receives the event after payment commit
- **THEN** D records only the `PAID_ORDER/SHOW` behavior event
- **AND** D does not create or update a behavior tag
- **AND** payment remains successful

### Requirement: Existing invalidation event remains unchanged

A SHALL NOT add fields or new interfaces to `OrderInvalidated` for this change. Existing payment, travel, and refund flows SHALL remain functional when `movieId` is null.

#### Scenario: Refund contract remains stable

- **GIVEN** an order is refunded after a payment event with a nullable movie ID
- **WHEN** A publishes `OrderInvalidated`
- **THEN** the event keeps its existing fields and version semantics
- **AND** no new profile refund interface is required

