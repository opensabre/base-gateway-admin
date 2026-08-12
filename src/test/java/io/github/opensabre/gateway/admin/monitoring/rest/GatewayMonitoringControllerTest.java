package io.github.opensabre.gateway.admin.monitoring.rest;

import io.github.opensabre.gateway.admin.integration.PrometheusReadClient;
import io.github.opensabre.gateway.admin.monitoring.service.GatewayRuntimeMonitoringService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayMonitoringControllerTest {

    @Test
    void executesOnlyServerDefinedRouteQueries() {
        PrometheusReadClient prometheus = mock(PrometheusReadClient.class);
        when(prometheus.query(contains("spring_cloud_gateway_requests_seconds"))).thenReturn("{}");

        var snapshot = new GatewayMonitoringController(prometheus,
                mock(GatewayRuntimeMonitoringService.class)).routes();

        assertThat(snapshot.requestRate()).isEqualTo("{}");
        assertThat(snapshot.errorRate()).isEqualTo("{}");
        assertThat(snapshot.p95Latency()).isEqualTo("{}");
        verify(prometheus).query(contains("count[5m]"));
        verify(prometheus).query(contains("status=~\"5..\""));
        verify(prometheus).query(contains("bucket[5m]"));
    }

    @Test
    void returnsEmptyVectorsWhenPrometheusIsUnavailable() {
        PrometheusReadClient prometheus = mock(PrometheusReadClient.class);
        when(prometheus.query(contains("spring_cloud_gateway_requests_seconds")))
                .thenThrow(new IllegalStateException("unavailable"));

        var snapshot = new GatewayMonitoringController(prometheus,
                mock(GatewayRuntimeMonitoringService.class)).routes();

        assertThat(snapshot.requestRate()).contains("\"result\":[]");
        assertThat(snapshot.errorRate()).contains("\"result\":[]");
        assertThat(snapshot.p95Latency()).contains("\"result\":[]");
    }
}
