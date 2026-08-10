package io.github.opensabre.gateway.admin.monitoring.model;

/** 固定 PromQL 查询的原始快照，由管理端按 routeId 合并展示。 */
public record GatewayRouteMetricsSnapshot(String requestRate, String errorRate, String p95Latency) {
}
