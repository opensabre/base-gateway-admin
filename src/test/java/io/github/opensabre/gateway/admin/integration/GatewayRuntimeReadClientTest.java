package io.github.opensabre.gateway.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import tools.jackson.databind.ObjectMapper;

class GatewayRuntimeReadClientTest {

    @Test
    void buildsRuntimeUriFromManagementMetadata() {
        var instance = new GatewayServiceInstance("10.0.0.8", 8443, "DEFAULT", true, true, 1.0,
                Map.of("management.scheme", "https", "management.host", "base-gateway",
                        "management.port", "18080", "management.path", "/manage"));

        var uri = new GatewayRuntimeReadClient(null, new ObjectMapper()).runtimeUri(instance);

        assertThat(uri.toString()).isEqualTo("https://base-gateway:18080/manage/gatewayruntime");
    }
}
