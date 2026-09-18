package io.github.opensabre.gateway.admin.monitoring.service;

import io.github.opensabre.gateway.admin.monitoring.integration.PrometheusQueryClient;
import io.github.opensabre.gateway.admin.monitoring.integration.PrometheusQueryException;
import io.github.opensabre.gateway.admin.monitoring.model.MonitoringDataSourceStatus;
import io.github.opensabre.gateway.admin.monitoring.model.MonitoringHistory;
import io.github.opensabre.gateway.admin.monitoring.model.MonitoringRange;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executes only allow-listed monitoring queries; callers never provide PromQL. */
@Service
public class MonitoringQueryService {

    private final PrometheusQueryClient prometheus;
    private final Clock clock;

    @Autowired
    public MonitoringQueryService(PrometheusQueryClient prometheus) {
        this(prometheus, Clock.systemUTC());
    }

    MonitoringQueryService(PrometheusQueryClient prometheus, Clock clock) {
        this.prometheus = prometheus;
        this.clock = clock;
    }

    public String query(String promql) {
        return prometheus.query(promql);
    }

    public MonitoringHistory routeHistory(String rangeValue, String routeId) {
        String selector = optionalLabel("routeId", routeId);
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tps", "sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count" + selector + "[5m]))");
        queries.put("errorTps", "sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count"
                + mergeSelector(selector, "status=~\"5..\"") + "[5m]))");
        queries.put("p50", gatewayQuantile(selector, "0.50"));
        queries.put("p95", gatewayQuantile(selector, "0.95"));
        queries.put("p99", gatewayQuantile(selector, "0.99"));
        return history(rangeValue, queries);
    }

    public MonitoringHistory applicationHistory(String rangeValue, String application, String instance) {
        String selector = labels(application, instance);
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tps", "sum by (application, instance) (rate(http_server_requests_seconds_count"
                + selector + "[5m]))");
        queries.put("errorTps", "sum by (application, instance) (rate(http_server_requests_seconds_count"
                + mergeSelector(selector, "status=~\"5..\"") + "[5m]))");
        queries.put("p95", applicationQuantile(selector, "0.95"));
        queries.put("p99", applicationQuantile(selector, "0.99"));
        queries.put("cpu", "max by (application, instance) (process_cpu_usage" + selector + ")");
        queries.put("heapUsed", "sum by (application, instance) (jvm_memory_used_bytes"
                + mergeSelector(selector, "area=\"heap\"") + ")");
        queries.put("heapMax", "sum by (application, instance) (jvm_memory_max_bytes"
                + mergeSelector(selector, "area=\"heap\"") + ")");
        return history(rangeValue, queries);
    }

    public MonitoringDataSourceStatus status() {
        try {
            return new MonitoringDataSourceStatus(true, "Prometheus query succeeded",
                    prometheus.query("up{job=\"opensabre-applications\"}"));
        } catch (PrometheusQueryException unavailable) {
            return new MonitoringDataSourceStatus(false, unavailable.getMessage(), null);
        }
    }

    private MonitoringHistory history(String rangeValue, Map<String, String> queries) {
        MonitoringRange range = MonitoringRange.parse(rangeValue);
        Instant end = clock.instant();
        Instant start = end.minus(range.duration());
        Map<String, String> results = new LinkedHashMap<>();
        queries.forEach((name, promql) ->
                results.put(name, prometheus.queryRange(promql, start, end, range.step())));
        return new MonitoringHistory(range.value(), start, end, range.step().toSeconds(), Map.copyOf(results));
    }

    private static String gatewayQuantile(String selector, String quantile) {
        return "histogram_quantile(" + quantile + ", sum by (routeId, le) "
                + "(rate(spring_cloud_gateway_requests_seconds_bucket" + selector + "[5m])))";
    }

    private static String applicationQuantile(String selector, String quantile) {
        return "histogram_quantile(" + quantile + ", sum by (application, instance, le) "
                + "(rate(http_server_requests_seconds_bucket" + selector + "[5m])))";
    }

    private static String labels(String application, String instance) {
        String result = optionalLabel("application", application);
        if (instance != null && !instance.isBlank()) {
            result = mergeSelector(result, "instance=\"" + labelValue(instance) + "\"");
        }
        return result;
    }

    private static String optionalLabel(String name, String value) {
        return value == null || value.isBlank() ? "" : "{" + name + "=\"" + labelValue(value) + "\"}";
    }

    private static String mergeSelector(String selector, String label) {
        if (selector == null || selector.isBlank()) return "{" + label + "}";
        return selector.substring(0, selector.length() - 1) + "," + label + "}";
    }

    private static String labelValue(String value) {
        if (value.length() > 255 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid monitoring label value");
        }
        return value;
    }
}
