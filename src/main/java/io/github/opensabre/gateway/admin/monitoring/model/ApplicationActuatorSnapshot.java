package io.github.opensabre.gateway.admin.monitoring.model;

/** Safe instantaneous JVM and process values read from standard Actuator metrics. */
public record ApplicationActuatorSnapshot(double processCpuUsage, long heapUsedBytes,
        long heapMaxBytes, long uptimeSeconds, int liveThreads) {
}
