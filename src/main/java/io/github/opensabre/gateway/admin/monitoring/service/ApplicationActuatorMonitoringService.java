package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.integration.ActuatorMetricsReadClient;
import io.github.opensabre.gateway.admin.monitoring.model.ApplicationInstanceActuator;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Builds basic Actuator snapshots for the Nacos service page requested by the management UI. */
@Service
public class ApplicationActuatorMonitoringService {
    private final GatewayServiceCatalogService catalogService;
    private final ActuatorMetricsReadClient actuatorClient;

    public ApplicationActuatorMonitoringService(GatewayServiceCatalogService catalogService,
            ActuatorMetricsReadClient actuatorClient) {
        this.catalogService = catalogService;
        this.actuatorClient = actuatorClient;
    }

    /** Read each node independently so one unavailable application remains visible as an error row. */
    public List<ApplicationInstanceActuator> snapshots(int page, int pageSize) {
        List<ApplicationInstanceActuator> result = new ArrayList<>();
        for (var service : catalogService.listServices(page, pageSize).services()) {
            for (GatewayServiceInstance instance : service.instances()) {
                String id = instance.ip() + ":" + instance.port();
                try {
                    result.add(new ApplicationInstanceActuator(service.name(), id, instance.healthy(),
                            actuatorClient.fetch(instance), null));
                } catch (IllegalStateException | IllegalArgumentException unavailable) {
                    result.add(new ApplicationInstanceActuator(service.name(), id, instance.healthy(), null,
                            unavailable.getMessage()));
                }
            }
        }
        return List.copyOf(result);
    }
}
