package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.monitoring.ApplicationMonitoringService;
import io.github.opensabre.monitoring.model.ApplicationInstanceMonitoring;
import io.github.opensabre.monitoring.model.MonitoringInstance;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Builds basic Actuator snapshots for the Nacos service page requested by the management UI. */
@Service
public class ApplicationActuatorMonitoringService {
    private final GatewayServiceCatalogService catalogService;
    private final ApplicationMonitoringService monitoringService;

    public ApplicationActuatorMonitoringService(GatewayServiceCatalogService catalogService,
            ApplicationMonitoringService monitoringService) {
        this.catalogService = catalogService;
        this.monitoringService = monitoringService;
    }

    /** Read each node independently so one unavailable application remains visible as an error row. */
    public List<ApplicationInstanceMonitoring> snapshots(int page, int pageSize) {
        List<ApplicationInstanceMonitoring> result = new ArrayList<>();
        for (var service : catalogService.listServices(page, pageSize).services()) {
            List<MonitoringInstance> instances = service.instances().stream()
                    .map(instance -> new MonitoringInstance(instance.ip(), instance.port(),
                            instance.healthy(), instance.metadata()))
                    .toList();
            result.addAll(monitoringService.snapshots(service.name(), instances));
        }
        return List.copyOf(result);
    }
}
