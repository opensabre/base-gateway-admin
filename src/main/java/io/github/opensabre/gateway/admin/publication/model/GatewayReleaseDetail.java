package io.github.opensabre.gateway.admin.publication.model;

import java.util.List;

/** 发布记录及其不可变影响项。 */
public record GatewayReleaseDetail(GatewayRelease release, List<GatewayReleaseItem> items,
                                   List<GatewayInstanceRevision> instances,
                                   List<GatewayRouteProbe> routeProbes) {
}
