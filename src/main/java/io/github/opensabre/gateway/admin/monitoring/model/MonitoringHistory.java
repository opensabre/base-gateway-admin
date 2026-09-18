package io.github.opensabre.gateway.admin.monitoring.model;

import java.time.Instant;
import java.util.Map;

/** Raw Prometheus matrices keyed by a stable server-defined metric name. */
public record MonitoringHistory(String range, Instant start, Instant end, long stepSeconds,
        Map<String, String> series) {
}
