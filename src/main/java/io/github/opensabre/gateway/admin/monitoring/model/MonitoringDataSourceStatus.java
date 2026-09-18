package io.github.opensabre.gateway.admin.monitoring.model;

/** Explicit Prometheus state; an empty successful vector is not an outage. */
public record MonitoringDataSourceStatus(boolean available, String message, String targets) {
}
