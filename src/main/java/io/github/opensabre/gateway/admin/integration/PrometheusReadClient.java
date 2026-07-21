package io.github.opensabre.gateway.admin.integration;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Prometheus 即时查询只读客户端，监控数据不写入控制面业务库。
 */
@Component
public class PrometheusReadClient {

    private final HttpClient httpClient;
    private final GatewayIntegrationProperties.Prometheus properties;

    @Autowired
    public PrometheusReadClient(GatewayIntegrationProperties properties) {
        this(properties.getPrometheus(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    PrometheusReadClient(GatewayIntegrationProperties.Prometheus properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** 执行受控的 PromQL 即时查询，返回 Prometheus 原始 JSON。 */
    public String query(String promql) {
        if (promql == null || promql.isBlank()) {
            throw new IllegalArgumentException("PromQL 不能为空");
        }
        HttpRequest request = HttpRequest.newBuilder(queryUri(promql)).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("查询 Prometheus 失败，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("查询 Prometheus 失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询 Prometheus 被中断", exception);
        }
    }

    URI queryUri(String promql) {
        String baseUrl = properties.getServerUrl().replaceAll("/$", "");
        return URI.create(baseUrl + "/api/v1/query?query="
                + URLEncoder.encode(promql, StandardCharsets.UTF_8));
    }
}
