package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.monitoring.ApplicationMonitoringService;
import io.github.opensabre.monitoring.model.ApplicationInstanceMonitoring;
import io.github.opensabre.monitoring.model.ApplicationRuntimeMetrics;
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
        var monitoring = mock(ApplicationMonitoringService.class);
        var healthy = instance("10.0.0.1", true);
        var unavailable = instance("10.0.0.2", false);
        when(catalog.listServices(1, 20)).thenReturn(new GatewayServicePage(1, 1, 20,
                List.of(new GatewayServiceSummary("base-sysadmin", 2, 1, List.of(healthy, unavailable)))));
        when(monitoring.snapshots(org.mockito.ArgumentMatchers.eq("base-sysadmin"),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(
                        new ApplicationInstanceMonitoring("base-sysadmin", "10.0.0.1:8080", true,
                                new ApplicationRuntimeMetrics(0.25, 100, 200, 60, 12), null),
                        new ApplicationInstanceMonitoring("base-sysadmin", "10.0.0.2:8080", false,
                                null, "HTTP 401")));

        var result = new ApplicationActuatorMonitoringService(catalog, monitoring).snapshots(1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).snapshot().processCpuUsage()).isEqualTo(0.25);
        assertThat(result.get(1).snapshot()).isNull();
        assertThat(result.get(1).errorMessage()).isEqualTo("HTTP 401");
        org.mockito.Mockito.verify(monitoring).snapshots(org.mockito.ArgumentMatchers.eq("base-sysadmin"),
                org.mockito.ArgumentMatchers.argThat(instances -> instances.size() == 2
                        && instances.get(0).host().equals("10.0.0.1")));
    }

    private GatewayServiceInstance instance(String ip, boolean healthy) {
        return new GatewayServiceInstance(ip, 8080, "DEFAULT", healthy, true, 1.0, Map.of());
    }
}
