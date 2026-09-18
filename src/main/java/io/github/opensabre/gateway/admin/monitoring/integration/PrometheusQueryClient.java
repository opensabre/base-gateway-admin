package io.github.opensabre.gateway.admin.monitoring.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Prometheus query adapter owned by the gateway monitoring control plane. */
@Component
public class PrometheusQueryClient {

    private final HttpClient httpClient;
    private final String serverUrl;
    private final Duration readTimeout;

    @Autowired
    public PrometheusQueryClient(
            @Value("${opensabre.gateway-admin.monitoring.prometheus-url:${PROMETHEUS_URL:http://localhost:9090}}")
            String serverUrl,
            @Value("${opensabre.gateway-admin.monitoring.connect-timeout:2s}") Duration connectTimeout,
            @Value("${opensabre.gateway-admin.monitoring.read-timeout:5s}") Duration readTimeout) {
        this(HttpClient.newBuilder().connectTimeout(connectTimeout).build(), serverUrl, readTimeout);
    }

    PrometheusQueryClient(HttpClient httpClient, String serverUrl, Duration readTimeout) {
        this.httpClient = httpClient;
        this.serverUrl = serverUrl.replaceAll("/$", "");
        this.readTimeout = readTimeout;
    }

    public String query(String promql) {
        return get("/api/v1/query?query=" + encode(promql));
    }

    public String queryRange(String promql, Instant start, Instant end, Duration step) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Query end must be after start");
        }
        return get("/api/v1/query_range?query=" + encode(promql)
                + "&start=" + start.getEpochSecond()
                + "&end=" + end.getEpochSecond()
                + "&step=" + step.toSeconds());
    }

    private String get(String pathAndQuery) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverUrl + pathAndQuery))
                .timeout(readTimeout).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PrometheusQueryException("Prometheus returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new PrometheusQueryException("Prometheus is unreachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PrometheusQueryException("Prometheus query was interrupted", exception);
        }
    }

    private static String encode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PromQL must not be blank");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
