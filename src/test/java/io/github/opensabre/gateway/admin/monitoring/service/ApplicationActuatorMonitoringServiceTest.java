package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.integration.ActuatorMetricsReadClient;
import io.github.opensabre.gateway.admin.monitoring.model.ApplicationActuatorSnapshot;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import io.github.opensabre.gateway.admin.service.model.GatewayServicePage;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationActuatorMonitoringServiceTest {

    @Test
    void keepsUnavailableInstancesVisible() {
        var catalog = mock(GatewayServiceCatalogService.class);
        var client = mock(ActuatorMetricsReadClient.class);
        var healthy = instance("10.0.0.1", true);
        var unavailable = instance("10.0.0.2", false);
        when(catalog.listServices(1, 20)).thenReturn(new GatewayServicePage(1, 1, 20,
                List.of(new GatewayServiceSummary("base-sysadmin", 2, 1, List.of(healthy, unavailable)))));
        when(client.fetch(healthy)).thenReturn(new ApplicationActuatorSnapshot(0.25, 100, 200, 60, 12));
        when(client.fetch(unavailable)).thenThrow(new IllegalStateException("HTTP 401"));

        var result = new ApplicationActuatorMonitoringService(catalog, client).snapshots(1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).snapshot().processCpuUsage()).isEqualTo(0.25);
        assertThat(result.get(1).snapshot()).isNull();
        assertThat(result.get(1).errorMessage()).isEqualTo("HTTP 401");
    }

    private GatewayServiceInstance instance(String ip, boolean healthy) {
        return new GatewayServiceInstance(ip, 8080, "DEFAULT", healthy, true, 1.0, Map.of());
    }
}
