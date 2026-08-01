package io.github.opensabre.gateway.admin.publication.model;

import io.github.opensabre.gateway.admin.route.model.GatewayRoute;

import java.util.List;
import java.util.Map;

/** 通过发布预检后生成的托管路由和熔断实例候选。 */
public record ReleaseValidationResult(
        String baseVersion,
        int apiRouteCount,
        int applicationRouteCount,
        List<GatewayRoute> managedRoutes,
        Map<String, Map<String, Object>> circuitBreakerInstances) {
}
