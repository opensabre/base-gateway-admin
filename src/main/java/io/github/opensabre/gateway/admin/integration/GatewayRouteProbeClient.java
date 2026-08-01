package io.github.opensabre.gateway.admin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** 调用单个网关实例的内部 Route ID 装载探测端点。 */
@Component
public class GatewayRouteProbeClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GatewayRouteProbeClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), objectMapper);
    }

    GatewayRouteProbeClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** 返回实例缺失的 Route ID；空集合表示全部装载。 */
    public List<String> probe(GatewayServiceInstance instance, String revision, List<String> routeIds) {
        String scheme = instance.metadata().getOrDefault("revision.scheme", "http");
        String path = instance.metadata().getOrDefault("route-probe.path", "/internal/gateway/routes/probe");
        if (!path.startsWith("/")) throw new IllegalArgumentException("路由探测路径必须以 / 开头");
        URI uri = URI.create(scheme + "://" + instance.ip() + ":" + instance.port() + path);
        try {
            String body = objectMapper.writeValueAsString(new ProbeRequest(revision, routeIds));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("实例路由探测接口返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!revision.equals(root.path("revision").asText())) {
                throw new IllegalStateException("实例路由探测修订号不一致");
            }
            return objectMapper.convertValue(root.path("missing"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("实例路由探测被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法执行网关实例路由探测", exception);
        }
    }

    private record ProbeRequest(String revision, List<String> routeIds) {
    }
}
