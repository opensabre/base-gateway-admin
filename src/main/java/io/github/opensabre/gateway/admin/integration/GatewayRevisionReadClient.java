package io.github.opensabre.gateway.admin.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 直接查询单个网关实例实际加载的发布修订号。 */
@Component
public class GatewayRevisionReadClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GatewayRevisionReadClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), objectMapper);
    }

    GatewayRevisionReadClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** 返回实例内存中的 revision；非 200、超时或无效响应均视为不可达。 */
    public String fetch(GatewayServiceInstance instance) {
        String scheme = instance.metadata().getOrDefault("revision.scheme", "http");
        String path = instance.metadata().getOrDefault("revision.path", "/internal/gateway/revision");
        if (!path.startsWith("/")) throw new IllegalArgumentException("修订号探测路径必须以 / 开头");
        URI uri = URI.create(scheme + "://" + instance.ip() + ":" + instance.port() + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("实例修订号接口返回 HTTP " + response.statusCode());
            }
            String revision = objectMapper.readTree(response.body()).path("revision").asText();
            if (revision.isBlank()) throw new IllegalStateException("实例修订号响应为空");
            return revision;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("实例修订号查询被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取网关实例修订号", exception);
        }
    }
}
