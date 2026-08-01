package io.github.opensabre.gateway.admin.publication.model;

import java.util.List;

/** 发布后路由装载探测汇总。 */
public record GatewayRouteProbeSummary(int total, int passed, List<GatewayRouteProbe> probes) {
    public boolean allPassed() {
        return total > 0 && total == passed;
    }
}
