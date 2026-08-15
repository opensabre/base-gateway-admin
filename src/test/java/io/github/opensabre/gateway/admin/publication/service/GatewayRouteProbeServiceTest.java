package io.github.opensabre.gateway.admin.publication.service;

import tools.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.integration.GatewayRouteProbeClient;
import io.github.opensabre.gateway.admin.publication.dao.GatewayRouteProbeMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeStatus;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeSummary;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 无下游业务调用的 Route ID 装载探测汇总测试。 */
class GatewayRouteProbeServiceTest {

    @Test
    void shouldReportMissingAndUnreachableRoutesPerInstance() {
        GatewayServiceCatalogService catalog = mock(GatewayServiceCatalogService.class);
        GatewayRouteProbeClient client = mock(GatewayRouteProbeClient.class);
        GatewayRouteProbeMapper mapper = mock(GatewayRouteProbeMapper.class);
        GatewayServiceInstance first = instance("10.0.0.1");
        GatewayServiceInstance second = instance("10.0.0.2");
        when(catalog.getService("base-gateway")).thenReturn(
                new GatewayServiceSummary("base-gateway", 2, 2, List.of(first, second)));
        when(client.probe(first, "release-9", List.of("api-1", "api-2"))).thenReturn(List.of("api-2"));
        when(client.probe(second, "release-9", List.of("api-1", "api-2")))
                .thenThrow(new IllegalStateException("timeout"));
        when(mapper.insert(any(io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbe.class))).thenReturn(1);
        GatewayRouteProbeService service = new GatewayRouteProbeService(
                catalog, client, mapper, new ObjectMapper(), "base-gateway");

        GatewayRouteProbeSummary result = service.probe("release-9", List.of("api-1", "api-2"));

        assertThat(result.allPassed()).isFalse();
        assertThat(result.probes()).extracting(item -> item.getStatus()).containsExactly(
                GatewayRouteProbeStatus.MISSING, GatewayRouteProbeStatus.UNREACHABLE);
        assertThat(result.probes().get(0).getMissingRouteIdsJson()).isEqualTo("[\"api-2\"]");
    }

    private GatewayServiceInstance instance(String ip) {
        return new GatewayServiceInstance(ip, 8443, "DEFAULT", true, true, 1.0, Map.of());
    }
}
