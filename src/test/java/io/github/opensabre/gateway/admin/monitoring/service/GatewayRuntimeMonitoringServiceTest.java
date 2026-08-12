package io.github.opensabre.gateway.admin.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.opensabre.gateway.admin.integration.GatewayRuntimeReadClient;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayRuntimeSnapshot;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceSummary;

class GatewayRuntimeMonitoringServiceTest {

    @Test
    void keepsUnavailableInstancesVisible() {
        GatewayServiceCatalogService catalog = mock(GatewayServiceCatalogService.class);
        GatewayRuntimeReadClient client = mock(GatewayRuntimeReadClient.class);
        GatewayServiceInstance healthy = instance("10.0.0.1", true);
        GatewayServiceInstance unavailable = instance("10.0.0.2", false);
        when(catalog.getService("base-gateway")).thenReturn(
                new GatewayServiceSummary("base-gateway", 2, 1, List.of(healthy, unavailable)));
        when(client.fetch(healthy)).thenReturn(snapshot());
        when(client.fetch(unavailable)).thenThrow(new IllegalStateException("timeout"));

        var result = new GatewayRuntimeMonitoringService(catalog, client, "base-gateway").snapshots();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).snapshot()).isNotNull();
        assertThat(result.get(1).instanceId()).isEqualTo("10.0.0.2:8443");
        assertThat(result.get(1).errorMessage()).isEqualTo("timeout");
    }

    private GatewayServiceInstance instance(String ip, boolean healthy) {
        return new GatewayServiceInstance(ip, 8443, "DEFAULT", healthy, true, 1, Map.of());
    }

    private GatewayRuntimeSnapshot snapshot() {
        return new GatewayRuntimeSnapshot("1", "1.0", 1, 4,
                new GatewayRuntimeSnapshot.JvmSnapshot("21", "vendor", 1, 2, 3, 4, 5),
                new GatewayRuntimeSnapshot.NettySnapshot(4, -1),
                new GatewayRuntimeSnapshot.HttpClientSnapshot("FIXED", 500, 45000, 30000,
                        5000L, null, null), 10, Map.of());
    }
}
