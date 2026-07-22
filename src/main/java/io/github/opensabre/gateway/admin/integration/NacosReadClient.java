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
 * Nacos 只读客户端。当前只允许查询服务目录和网关配置，防止迁移期出现双写。
 */
@Component
public class NacosReadClient {

    private final HttpClient httpClient;
    private final GatewayIntegrationProperties.Nacos properties;

    @Autowired
    public NacosReadClient(GatewayIntegrationProperties properties) {
        this(properties.getNacos(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    NacosReadClient(GatewayIntegrationProperties.Nacos properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** 查询 Nacos 服务名分页列表，返回 Nacos 原始 JSON。 */
    public String listServices(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("服务分页参数超出允许范围");
        }
        return get(serviceListUri(page, pageSize), "查询 Nacos 服务列表");
    }

    /** 查询指定服务的全部实例，包含健康和集群信息。 */
    public String listInstances(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("服务名不能为空");
        }
        return get(instanceListUri(serviceName), "查询 Nacos 服务实例");
    }

    /** 查询当前网关运行配置，返回 Nacos 原始 YAML。 */
    public String getGatewayConfig() {
        return get(gatewayConfigUri(), "读取网关配置");
    }

    URI serviceListUri(int page, int pageSize) {
        return uri("/nacos/v1/ns/service/list?pageNo=" + page + "&pageSize=" + pageSize
                + "&groupName=" + encode(properties.getGroup())
                + "&namespaceId=" + encode(properties.getNamespace()));
    }

    URI gatewayConfigUri() {
        return uri("/nacos/v1/cs/configs?dataId=" + encode(properties.getGatewayDataId())
                + "&group=" + encode(properties.getGroup())
                + "&tenant=" + encode(properties.getNamespace()));
    }

    URI instanceListUri(String serviceName) {
        return uri("/nacos/v1/ns/instance/list?serviceName=" + encode(serviceName)
                + "&groupName=" + encode(properties.getGroup())
                + "&namespaceId=" + encode(properties.getNamespace())
                + "&healthyOnly=false");
    }

    private String get(URI uri, String operation) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException(operation + "失败，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException(operation + "失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + "被中断", exception);
        }
    }

    private URI uri(String path) {
        return URI.create(properties.getServerUrl().replaceAll("/$", "") + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
