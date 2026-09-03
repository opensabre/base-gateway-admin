package io.github.opensabre.gateway.admin.monitoring.model;

/** Per-instance Actuator snapshot; one inaccessible node does not fail the whole service catalog. */
public record ApplicationInstanceActuator(String serviceName, String instanceId, boolean healthy,
        ApplicationActuatorSnapshot snapshot, String errorMessage) {
}
