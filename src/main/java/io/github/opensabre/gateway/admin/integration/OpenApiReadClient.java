package io.github.opensabre.gateway.admin.integration;

import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 通过服务健康实例的内部地址读取 OpenAPI 文档。 */
@Component
public class OpenApiReadClient {

    private final HttpClient httpClient;

    @Autowired
    public OpenApiReadClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    OpenApiReadClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** 读取实例 OpenAPI；路径和协议可由 Nacos 实例 metadata 覆盖。 */
    public String fetch(GatewayServiceInstance instance) {
        String scheme = instance.metadata().getOrDefault("openapi.scheme", "http");
        String path = instance.metadata().getOrDefault("openapi.path", "/v3/api-docs");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("OpenAPI 路径必须以 / 开头");
        }
        URI uri = URI.create(scheme + "://" + instance.ip() + ":" + instance.port() + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("读取 OpenAPI 失败，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("读取 OpenAPI 失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取 OpenAPI 被中断", exception);
        }
    }
}
