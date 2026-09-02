package io.github.opensabre.gateway.admin.monitoring.model;

/**
 * Fixed Prometheus vectors used by the service catalog to render basic per-instance metrics.
 * Raw vectors preserve Prometheus labels so the frontend can associate them with Nacos instances.
 */
public record ApplicationMetricsSnapshot(String requestRate, String errorRate, String p95Latency,
        String cpuUsage, String heapUsed, String heapMax) {
}
