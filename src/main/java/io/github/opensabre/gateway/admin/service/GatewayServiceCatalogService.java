package io.github.opensabre.gateway.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.integration.NacosReadClient;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import io.github.opensabre.gateway.admin.service.model.GatewayServicePage;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 Nacos 原始响应转换为稳定的网关服务目录模型。 */
@Service
public class GatewayServiceCatalogService {

    private final NacosReadClient nacosReadClient;
    private final ObjectMapper objectMapper;

    public GatewayServiceCatalogService(NacosReadClient nacosReadClient, ObjectMapper objectMapper) {
        this.nacosReadClient = nacosReadClient;
        this.objectMapper = objectMapper;
    }

    /** 查询服务分页，并补充每个服务的实例健康状态。 */
    public GatewayServicePage listServices(int page, int pageSize) {
        try {
            JsonNode root = objectMapper.readTree(nacosReadClient.listServices(page, pageSize));
            List<GatewayServiceSummary> services = new ArrayList<>();
            for (JsonNode name : root.path("doms")) {
                services.add(parseService(name.asText(), nacosReadClient.listInstances(name.asText())));
            }
            return new GatewayServicePage(root.path("count").asLong(), page, pageSize, List.copyOf(services));
        } catch (Exception exception) {
            throw new IllegalStateException("解析 Nacos 服务目录失败", exception);
        }
    }

    GatewayServiceSummary parseService(String serviceName, String response) throws Exception {
        JsonNode hosts = objectMapper.readTree(response).path("hosts");
        List<GatewayServiceInstance> instances = new ArrayList<>();
        int healthyCount = 0;
        for (JsonNode host : hosts) {
            boolean healthy = host.path("healthy").asBoolean();
            if (healthy) healthyCount++;
            instances.add(new GatewayServiceInstance(host.path("ip").asText(), host.path("port").asInt(),
                    host.path("clusterName").asText(), healthy, host.path("enabled").asBoolean(),
                    host.path("weight").asDouble(), metadata(host.path("metadata"))));
        }
        return new GatewayServiceSummary(serviceName, instances.size(), healthyCount, List.copyOf(instances));
    }

    private Map<String, String> metadata(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(field -> result.put(field.getKey(), field.getValue().asText()));
        return Map.copyOf(result);
    }
}
