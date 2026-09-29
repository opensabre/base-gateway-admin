package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.monitoring.integration.PrometheusQueryClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({"30m,1800,15", "2h,7200,60"})
    void supportsIntermediateRangesForRouteAndApplicationHistory(String range, long seconds, long step) {
        PrometheusQueryClient client = mock(PrometheusQueryClient.class);
        when(client.queryRange(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        Instant end = Instant.parse("2026-09-18T08:00:00Z");
        var service = new MonitoringQueryService(client, Clock.fixed(end, ZoneOffset.UTC));

        var route = service.routeHistory(range, null);
        var application = service.applicationHistory(range, "base-organization", null);

        for (var history : java.util.List.of(route, application)) {
            assertThat(history.range()).isEqualTo(range);
            assertThat(history.start()).isEqualTo(end.minusSeconds(seconds));
            assertThat(history.end()).isEqualTo(end);
            assertThat(history.stepSeconds()).isEqualTo(step);
        }
        verify(client, org.mockito.Mockito.atLeastOnce()).queryRange(
                org.mockito.ArgumentMatchers.anyString(), eq(end.minusSeconds(seconds)), eq(end),
                eq(java.time.Duration.ofSeconds(step)));
    }

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
