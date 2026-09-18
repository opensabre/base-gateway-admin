package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.monitoring.integration.PrometheusQueryClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringQueryServiceTest {

    @Test
    void buildsBoundedRouteRangeQueries() {
        PrometheusQueryClient client = mock(PrometheusQueryClient.class);
        when(client.queryRange(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        Instant end = Instant.parse("2026-09-18T08:00:00Z");
        var service = new MonitoringQueryService(client, Clock.fixed(end, ZoneOffset.UTC));

        var history = service.routeHistory("1h", "base-sysadmin-api");

        assertThat(history.start()).isEqualTo(end.minusSeconds(3600));
        assertThat(history.stepSeconds()).isEqualTo(30);
        assertThat(history.series()).containsKeys("tps", "errorTps", "p50", "p95", "p99");
        verify(client).queryRange(org.mockito.ArgumentMatchers.argThat(query ->
                        query.contains("0.50") && query.contains("routeId=\"base-sysadmin-api\"")),
                eq(end.minusSeconds(3600)), eq(end), eq(java.time.Duration.ofSeconds(30)));
    }

    @Test
    void rejectsPromqlInjectionThroughLabels() {
        var service = new MonitoringQueryService(mock(PrometheusQueryClient.class));

        assertThatThrownBy(() -> service.routeHistory("1h", "route\"} or vector(1)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    void rejectsUnsupportedRanges() {
        var service = new MonitoringQueryService(mock(PrometheusQueryClient.class));

        assertThatThrownBy(() -> service.applicationHistory("90d", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range");
    }
}
