package io.github.opensabre.gateway.admin.integration;

import io.github.opensabre.gateway.admin.monitoring.model.ApplicationActuatorSnapshot;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Reads a fixed allow-list of standard Actuator metrics from a discovered application instance. */
@Component
public class ActuatorMetricsReadClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ActuatorMetricsReadClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), objectMapper);
    }

    ActuatorMetricsReadClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** Read only the process and JVM values displayed by the service catalog. */
    public ApplicationActuatorSnapshot fetch(GatewayServiceInstance instance) {
        double cpu = metric(instance, "process.cpu.usage", null);
        long heapUsed = Math.round(metric(instance, "jvm.memory.used", "area:heap"));
        long heapMax = Math.round(metric(instance, "jvm.memory.max", "area:heap"));
        long uptime = Math.round(metric(instance, "process.uptime", null));
        int liveThreads = (int) Math.round(metric(instance, "jvm.threads.live", null));
        return new ApplicationActuatorSnapshot(cpu, heapUsed, heapMax, uptime, liveThreads);
    }

    private double metric(GatewayServiceInstance instance, String metricName, String tag) {
        HttpRequest request = HttpRequest.newBuilder(metricUri(instance, metricName, tag))
                .timeout(Duration.ofSeconds(3)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Actuator 指标接口返回 HTTP " + response.statusCode());
            }
            JsonNode measurements = objectMapper.readTree(response.body()).path("measurements");
            for (JsonNode measurement : measurements) {
                if ("VALUE".equals(measurement.path("statistic").asText())) {
                    return measurement.path("value").asDouble();
                }
            }
            throw new IllegalStateException("Actuator 指标缺少 VALUE：" + metricName);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Actuator 指标查询被中断", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取 Actuator 指标：" + metricName, exception);
        }
    }

    URI metricUri(GatewayServiceInstance instance, String metricName, String tag) {
        String scheme = instance.metadata().getOrDefault("management.scheme",
                instance.metadata().getOrDefault("runtime.scheme", "http"));
        String host = instance.metadata().getOrDefault("management.host", instance.ip());
        String port = instance.metadata().getOrDefault("management.port", Integer.toString(instance.port()));
        String path = instance.metadata().getOrDefault("management.path", "/actuator");
        if (!path.startsWith("/")) throw new IllegalArgumentException("Actuator 路径必须以 / 开头");
        String uri = scheme + "://" + host + ":" + port + path + "/metrics/"
                + URLEncoder.encode(metricName, StandardCharsets.UTF_8);
        if (tag != null) uri += "?tag=" + URLEncoder.encode(tag, StandardCharsets.UTF_8);
        return URI.create(uri);
    }
}
