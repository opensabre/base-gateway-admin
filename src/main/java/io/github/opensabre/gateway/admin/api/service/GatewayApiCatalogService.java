package io.github.opensabre.gateway.admin.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.api.dao.GatewayApiMapper;
import io.github.opensabre.gateway.admin.api.model.ApiDiscoveryStatus;
import io.github.opensabre.gateway.admin.api.model.ApiSourceType;
import io.github.opensabre.gateway.admin.api.model.ApiSyncResult;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.integration.OpenApiReadClient;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从健康服务实例同步 OpenAPI，并维护 API 资产的 ACTIVE/MISSING 快照。
 */
@Service
public class GatewayApiCatalogService {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace");

    private final GatewayApiMapper mapper;
    private final GatewayServiceCatalogService serviceCatalog;
    private final OpenApiReadClient openApiReadClient;
    private final ObjectMapper objectMapper;

    public GatewayApiCatalogService(GatewayApiMapper mapper, GatewayServiceCatalogService serviceCatalog,
            OpenApiReadClient openApiReadClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.serviceCatalog = serviceCatalog;
        this.openApiReadClient = openApiReadClient;
        this.objectMapper = objectMapper;
    }

    /** 查询 API 资产，可按服务和发现状态筛选。 */
    public List<GatewayApi> list(String serviceId, ApiDiscoveryStatus status) {
        LambdaQueryWrapper<GatewayApi> query = new LambdaQueryWrapper<GatewayApi>()
                .eq(serviceId != null && !serviceId.isBlank(), GatewayApi::getServiceId, serviceId)
                .eq(status != null, GatewayApi::getDiscoveryStatus, status)
                .orderByAsc(GatewayApi::getServiceId)
                .orderByAsc(GatewayApi::getUpstreamPath)
                .orderByAsc(GatewayApi::getHttpMethod);
        return mapper.selectList(query);
    }

    /**
     * 只有文档读取和解析全部成功后才进入事务更新，失败不会把旧资产误标为 MISSING。
     */
    @Transactional
    public ApiSyncResult sync(String serviceId) {
        GatewayServiceInstance instance = serviceCatalog.getService(serviceId).instances().stream()
                .filter(item -> item.healthy() && item.enabled())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("服务没有可用的健康实例：" + serviceId));
        List<DiscoveredApi> discovered = parseOpenApi(openApiReadClient.fetch(instance));
        if (discovered.isEmpty()) {
            throw new IllegalStateException("OpenAPI 文档不包含任何可发现接口，保留上一份资产快照");
        }
        return applySnapshot(serviceId, discovered);
    }

    ApiSyncResult applySnapshot(String serviceId, List<DiscoveredApi> discovered) {
        List<GatewayApi> existing = mapper.selectList(new LambdaQueryWrapper<GatewayApi>()
                .eq(GatewayApi::getServiceId, serviceId));
        Map<String, GatewayApi> byIdentity = new HashMap<>();
        existing.forEach(item -> byIdentity.put(identity(item.getHttpMethod(), item.getUpstreamPath()), item));
        Set<String> seen = new HashSet<>();
        Date now = Date.from(Instant.now());
        int created = 0;
        int updated = 0;
        for (DiscoveredApi item : discovered) {
            String identity = identity(item.httpMethod(), item.path());
            if (!seen.add(identity)) {
                throw new IllegalArgumentException("OpenAPI 存在重复接口：" + identity);
            }
            GatewayApi current = byIdentity.get(identity);
            if (current == null) {
                mapper.insert(toEntity(serviceId, item, now));
                created++;
            } else if (current.getSourceType() == ApiSourceType.OPENAPI) {
                current.setOperationId(item.operationId());
                current.setSummary(item.summary());
                current.setTagsJson(writeTags(item.tags()));
                current.setSourceHash(item.sourceHash());
                current.setDiscoveryStatus(ApiDiscoveryStatus.ACTIVE);
                current.setLastDiscoveredTime(now);
                mapper.updateById(current);
                updated++;
            }
        }
        int missing = 0;
        for (GatewayApi current : existing) {
            if (current.getSourceType() == ApiSourceType.OPENAPI
                    && !seen.contains(identity(current.getHttpMethod(), current.getUpstreamPath()))
                    && current.getDiscoveryStatus() != ApiDiscoveryStatus.MISSING) {
                current.setDiscoveryStatus(ApiDiscoveryStatus.MISSING);
                current.setLastDiscoveredTime(now);
                mapper.updateById(current);
                missing++;
            }
        }
        return new ApiSyncResult(serviceId, discovered.size(), created, updated, missing);
    }

    /** 将 OpenAPI paths 转换为稳定的 Method + Path API 列表。 */
    List<DiscoveredApi> parseOpenApi(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.path("openapi").isTextual() || !root.path("paths").isObject()) {
                throw new IllegalArgumentException("文档不是有效的 OpenAPI 3 文档");
            }
            List<DiscoveredApi> result = new ArrayList<>();
            root.path("paths").fields().forEachRemaining(pathEntry ->
                    pathEntry.getValue().fields().forEachRemaining(operation -> {
                        String method = operation.getKey().toLowerCase(Locale.ROOT);
                        if (!HTTP_METHODS.contains(method)) {
                            return;
                        }
                        JsonNode value = operation.getValue();
                        List<String> tags = new ArrayList<>();
                        value.path("tags").forEach(tag -> tags.add(tag.asText()));
                        String operationId = text(value, "operationId");
                        String summary = text(value, "summary");
                        String hash = sha256(method + "\n" + pathEntry.getKey() + "\n"
                                + operationId + "\n" + summary + "\n" + String.join(",", tags));
                        result.add(new DiscoveredApi(method.toUpperCase(Locale.ROOT), pathEntry.getKey(),
                                operationId, summary, List.copyOf(tags), hash));
                    }));
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OpenAPI 文档不是有效 JSON", exception);
        }
    }

    private GatewayApi toEntity(String serviceId, DiscoveredApi item, Date now) {
        GatewayApi api = new GatewayApi();
        api.setServiceId(serviceId);
        api.setOperationId(item.operationId());
        api.setHttpMethod(item.httpMethod());
        api.setUpstreamPath(item.path());
        api.setSummary(item.summary());
        api.setTagsJson(writeTags(item.tags()));
        api.setSourceType(ApiSourceType.OPENAPI);
        api.setSourceHash(item.sourceHash());
        api.setDiscoveryStatus(ApiDiscoveryStatus.ACTIVE);
        api.setLastDiscoveredTime(now);
        return api;
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("API 标签无法序列化", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private static String identity(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    record DiscoveredApi(String httpMethod, String path, String operationId, String summary,
                         List<String> tags, String sourceHash) {
    }
}
