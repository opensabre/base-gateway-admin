package io.github.opensabre.gateway.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.integration.GatewayIntegrationProperties;
import io.github.opensabre.gateway.admin.integration.NacosReadClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
class GatewayServiceCatalogServiceTest {

    @Test
    void shouldParseInstanceHealthClusterAndMetadata() throws Exception {
        GatewayIntegrationProperties properties = new GatewayIntegrationProperties();
        GatewayServiceCatalogService service = new GatewayServiceCatalogService(
                new NacosReadClient(properties), new ObjectMapper());

        var summary = service.parseService("base-sysadmin", """
                {"hosts":[
                  {"ip":"10.0.0.2","port":8080,"clusterName":"DEFAULT","healthy":true,
                   "enabled":true,"weight":1.0,"metadata":{"version":"0.5.0"}},
                  {"ip":"10.0.0.3","port":8080,"clusterName":"canary","healthy":false,
                   "enabled":true,"weight":0.5,"metadata":{}}
                ]}
                """);

        assertThat(summary.name()).isEqualTo("base-sysadmin");
        assertThat(summary.instanceCount()).isEqualTo(2);
        assertThat(summary.healthyInstanceCount()).isEqualTo(1);
        assertThat(summary.instances().get(0).metadata()).containsEntry("version", "0.5.0");
    }
}
