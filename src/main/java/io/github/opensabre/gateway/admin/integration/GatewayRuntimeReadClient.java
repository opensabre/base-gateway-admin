package io.github.opensabre.gateway.admin.integration;

import tools.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayRuntimeSnapshot;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Reads the allow-listed runtime snapshot directly from one gateway instance. */
@Component
public class GatewayRuntimeReadClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GatewayRuntimeReadClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), objectMapper);
    }

    GatewayRuntimeReadClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public GatewayRuntimeSnapshot fetch(GatewayServiceInstance instance) {
        String scheme = instance.metadata().getOrDefault("runtime.scheme", "http");
        String path = instance.metadata().getOrDefault("runtime.path", "/actuator/gatewayruntime");
        if (!path.startsWith("/")) throw new IllegalArgumentException("运行参数路径必须以 / 开头");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(scheme + "://" + instance.ip() + ":" + instance.port() + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("实例运行参数接口返回 HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), GatewayRuntimeSnapshot.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("实例运行参数查询被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取网关实例运行参数", exception);
        }
    }
}
