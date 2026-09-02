package io.github.opensabre.gateway.admin.integration;

import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorMetricsReadClientTest {

    @Test
    void buildsMetricUriFromAllowListedInstanceMetadata() {
        var instance = new GatewayServiceInstance("10.0.0.8", 8080, "DEFAULT", true, true, 1.0,
                Map.of("management.scheme", "https", "management.host", "base-sysadmin",
                        "management.port", "9090",
                        "management.path", "/manage"));

        var uri = new ActuatorMetricsReadClient(null, new ObjectMapper())
                .metricUri(instance, "jvm.memory.used", "area:heap");

        assertThat(uri.toString()).isEqualTo(
                "https://base-sysadmin:9090/manage/metrics/jvm.memory.used?tag=area%3Aheap");
    }
}
