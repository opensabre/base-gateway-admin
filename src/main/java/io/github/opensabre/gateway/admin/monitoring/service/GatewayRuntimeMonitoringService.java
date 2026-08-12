package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.integration.GatewayRuntimeReadClient;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayInstanceRuntime;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GatewayRuntimeMonitoringService {
    private final GatewayServiceCatalogService catalogService;
    private final GatewayRuntimeReadClient runtimeReadClient;
    private final String gatewayServiceName;

    public GatewayRuntimeMonitoringService(GatewayServiceCatalogService catalogService,
            GatewayRuntimeReadClient runtimeReadClient,
            @Value("${opensabre.gateway-admin.gateway-service-name:base-gateway}") String gatewayServiceName) {
        this.catalogService = catalogService;
        this.runtimeReadClient = runtimeReadClient;
        this.gatewayServiceName = gatewayServiceName;
    }

    public List<GatewayInstanceRuntime> snapshots() {
        List<GatewayInstanceRuntime> result = new ArrayList<>();
        for (GatewayServiceInstance instance : catalogService.getService(gatewayServiceName).instances()) {
            String id = instance.ip() + ":" + instance.port();
            try {
                result.add(new GatewayInstanceRuntime(id, instance.healthy(),
                        runtimeReadClient.fetch(instance), null));
            } catch (IllegalStateException unavailable) {
                result.add(new GatewayInstanceRuntime(id, instance.healthy(), null,
                        unavailable.getMessage()));
            }
        }
        return List.copyOf(result);
    }
}
