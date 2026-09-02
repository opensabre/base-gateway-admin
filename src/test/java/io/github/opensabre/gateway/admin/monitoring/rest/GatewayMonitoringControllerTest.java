package io.github.opensabre.gateway.admin.monitoring.rest;

import io.github.opensabre.gateway.admin.integration.PrometheusReadClient;
import io.github.opensabre.gateway.admin.monitoring.service.GatewayRuntimeMonitoringService;
import io.github.opensabre.gateway.admin.monitoring.service.ApplicationActuatorMonitoringService;
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
                mock(GatewayRuntimeMonitoringService.class),
                mock(ApplicationActuatorMonitoringService.class)).routes();

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
                mock(GatewayRuntimeMonitoringService.class),
                mock(ApplicationActuatorMonitoringService.class)).routes();

        assertThat(snapshot.requestRate()).contains("\"result\":[]");
        assertThat(snapshot.errorRate()).contains("\"result\":[]");
        assertThat(snapshot.p95Latency()).contains("\"result\":[]");
    }

    @Test
    void executesOnlyServerDefinedApplicationQueries() {
        PrometheusReadClient prometheus = mock(PrometheusReadClient.class);
        when(prometheus.query(org.mockito.ArgumentMatchers.anyString())).thenReturn("{}");

        var snapshot = new GatewayMonitoringController(prometheus,
                mock(GatewayRuntimeMonitoringService.class),
                mock(ApplicationActuatorMonitoringService.class)).applications();

        assertThat(snapshot.requestRate()).isEqualTo("{}");
        assertThat(snapshot.errorRate()).isEqualTo("{}");
        assertThat(snapshot.p95Latency()).isEqualTo("{}");
        assertThat(snapshot.cpuUsage()).isEqualTo("{}");
        assertThat(snapshot.heapUsed()).isEqualTo("{}");
        assertThat(snapshot.heapMax()).isEqualTo("{}");
        verify(prometheus).query(contains("http_server_requests_seconds_count[5m]"));
        verify(prometheus).query(contains("status=~\"5..\""));
        verify(prometheus).query(contains("http_server_requests_seconds_bucket[5m]"));
        verify(prometheus).query(contains("process_cpu_usage"));
        verify(prometheus).query(contains("jvm_memory_used_bytes"));
        verify(prometheus).query(contains("jvm_memory_max_bytes"));
    }

    @Test
    void delegatesActuatorPageWithoutUsingPrometheus() {
        var actuator = mock(ApplicationActuatorMonitoringService.class);
        var controller = new GatewayMonitoringController(mock(PrometheusReadClient.class),
                mock(GatewayRuntimeMonitoringService.class), actuator);

        controller.actuator(2, 10);

        verify(actuator).snapshots(2, 10);
    }
}
