package com.miaoyu.ticket.travel.infrastructure.route;

import com.miaoyu.ticket.travel.application.BasicRouteProvider;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 固定 Demo 路线仅用于明确降级，绝不标记为真实导航。 */
final class DemoRouteProvider implements BasicRouteProvider {
    private static final String SOURCE = "DEMO_ROUTE_V1";

    @Override
    public Optional<BasicRouteResult> plan(
            ResolvedGeoPoint origin, ResolvedGeoPoint destination, String travelMode, OffsetDateTime requestedAt) {
        if (origin == null || destination == null || travelMode == null || travelMode.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BasicRouteResult(
                "DEMO", travelMode, 20, requestedAt.plusMinutes(40), SOURCE, requestedAt,
                requestedAt.plusMinutes(15), false, true, "DEMO_ROUTE"));
    }
}
