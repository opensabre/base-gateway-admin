package io.github.opensabre.gateway.admin.monitoring.model;

/** Per-instance runtime snapshot; failures remain visible instead of failing the entire page. */
public record GatewayInstanceRuntime(String instanceId, boolean healthy,
        GatewayRuntimeSnapshot snapshot, String errorMessage) {}
