package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.api.model.ApiDiscoveryStatus;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.publication.model.AuthMode;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.RiskLevel;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将 API 与应用级发布语义编译为 Spring Cloud Gateway Route。 */
@Component
public class GatewayRouteCompiler {

    private static final TypeReference<List<GatewayRouteDefinition>> DEFINITIONS = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    public GatewayRouteCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 编译候选路由；调用方负责只传入本次候选发布集合。 */
    public List<GatewayRoute> compile(List<ApiPublicationCandidate> apis,
            List<GatewayApplicationRoute> applicationRoutes) {
        List<GatewayRoute> routes = new ArrayList<>();
        Set<String> matchKeys = new HashSet<>();
        int apiOrder = -1000;
        for (ApiPublicationCandidate candidate : apis) {
            validateApi(candidate, matchKeys);
            routes.add(compileApi(candidate, apiOrder++));
        }
        int applicationOrder = 100;
        for (GatewayApplicationRoute route : applicationRoutes) {
            validateApplication(route, matchKeys);
            routes.add(compileApplication(route, applicationOrder++));
        }
        return List.copyOf(routes);
    }

    /** 宽通配路径必须明确标记为高风险。 */
    public RiskLevel classifyApplicationRisk(String path, String method) {
        if ("/**".equals(path) || path.matches("^/[^/]+/\\*\\*$")) {
            return RiskLevel.HIGH;
        }
        if (path.contains("**") || method == null || method.isBlank()) {
            return RiskLevel.HIGH;
        }
        return path.contains("*") ? RiskLevel.MEDIUM : RiskLevel.LOW;
    }

    private GatewayRoute compileApi(ApiPublicationCandidate candidate, int order) {
        GatewayApi api = candidate.api();
        GatewayApiPublication publication = candidate.publication();
        GatewayRoute route = baseRoute("api-" + api.getId(), "lb://" + api.getServiceId(), order);
        route.setMetadata(Map.of("opensabre-auth-mode", publication.getAuthMode().name()));
        route.setPredicates(List.of(definition("Method", "method", api.getHttpMethod()),
                definition("Path", "pattern", publication.getExternalPath())));
        List<GatewayRouteDefinition> configuredFilters = readDefinitions(publication.getFiltersJson());
        if (!configuredFilters.isEmpty()) {
            configuredFilters.forEach(this::validateDefinition);
            route.setFilters(configuredFilters);
            return route;
        }
        String targetPath = publication.getUpstreamPath() == null || publication.getUpstreamPath().isBlank()
                ? api.getUpstreamPath() : publication.getUpstreamPath();
        if (!targetPath.equals(publication.getExternalPath())) {
            route.setFilters(List.of(definition("SetPath", "template", targetPath)));
        }
        return route;
    }

    private GatewayRoute compileApplication(GatewayApplicationRoute declaration, int order) {
        int routeOrder = declaration.getRouteOrder() == null ? order : declaration.getRouteOrder();
        GatewayRoute route = baseRoute("application-" + declaration.getId(), declaration.getTargetUri(), routeOrder);
        List<GatewayRouteDefinition> configuredPredicates = readDefinitions(declaration.getPredicatesJson());
        List<GatewayRouteDefinition> configuredFilters = readDefinitions(declaration.getFiltersJson());
        if (!configuredPredicates.isEmpty()) {
            route.setPredicates(configuredPredicates);
            route.setFilters(configuredFilters);
            return route;
        }
        List<GatewayRouteDefinition> predicates = new ArrayList<>();
        if (declaration.getHttpMethod() != null && !declaration.getHttpMethod().isBlank()) {
            predicates.add(definition("Method", "method", declaration.getHttpMethod().toUpperCase(Locale.ROOT)));
        }
        predicates.add(definition("Path", "pattern", declaration.getExternalPath()));
        route.setPredicates(predicates);
        if (declaration.getRewritePath() != null && !declaration.getRewritePath().isBlank()) {
            String[] parts = declaration.getRewritePath().split("=>", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("应用路由 rewritePath 必须使用 regexp=>replacement 格式");
            }
            Map<String, String> args = new LinkedHashMap<>();
            args.put("regexp", parts[0].trim());
            args.put("replacement", parts[1].trim());
            route.setFilters(List.of(definition("RewritePath", args)));
        }
        return route;
    }

    private List<GatewayRouteDefinition> readDefinitions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, DEFINITIONS);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("应用路由断言或过滤器配置无法解析", exception);
        }
    }

    private void validateApi(ApiPublicationCandidate candidate, Set<String> matchKeys) {
        GatewayApi api = candidate.api();
        GatewayApiPublication publication = candidate.publication();
        if (api == null || publication == null || !api.getId().equals(publication.getApiId())) {
            throw new IllegalArgumentException("API 发布声明与 API 资产不匹配");
        }
        if (api.getDiscoveryStatus() == ApiDiscoveryStatus.MISSING) {
            throw new IllegalArgumentException("MISSING API 不能发布：" + api.getId());
        }
        validatePath(publication.getExternalPath(), false);
        readDefinitions(publication.getFiltersJson()).forEach(this::validateDefinition);
        if (publication.getAuthMode() == AuthMode.RESOURCE_REQUIRED
                && (publication.getResourceId() == null || publication.getResourceId().isBlank())) {
            throw new IllegalArgumentException("RESOURCE_REQUIRED API 必须关联资源");
        }
        addMatch(matchKeys, api.getHttpMethod(), publication.getExternalPath());
    }

    private void validateApplication(GatewayApplicationRoute route, Set<String> matchKeys) {
        List<GatewayRouteDefinition> configuredPredicates = readDefinitions(route.getPredicatesJson());
        String path = configuredPredicates.stream()
                .filter(definition -> "Path".equals(definition.getName()))
                .map(definition -> definition.getArgs().getOrDefault("pattern", definition.getArgs().get("value")))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(route.getExternalPath());
        if (!configuredPredicates.isEmpty() && (path == null || path.isBlank())) {
            throw new IllegalArgumentException("应用路由至少需要一个 Path 断言");
        }
        validatePath(path, true);
        configuredPredicates.forEach(this::validateDefinition);
        readDefinitions(route.getFiltersJson()).forEach(this::validateDefinition);
        if (route.getTargetUri() == null || !route.getTargetUri().matches("^(lb|https?)://.+")) {
            throw new IllegalArgumentException("应用路由目标 URI 仅支持 lb/http/https");
        }
        RiskLevel expected = classifyApplicationRisk(path, route.getHttpMethod());
        if (route.getRiskLevel() != expected) {
            throw new IllegalArgumentException("应用路由风险等级应为 " + expected);
        }
        addMatch(matchKeys, route.getHttpMethod(), path);
    }

    private void validateDefinition(GatewayRouteDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("路由断言和过滤器名称不能为空");
        }
        if (definition.getArgs() == null) {
            definition.setArgs(Map.of());
        }
    }

    private void validatePath(String path, boolean wildcardAllowed) {
        if (path == null || !path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("路由 Path 必须是以 / 开头且不含查询串的路径");
        }
        if (!wildcardAllowed && path.contains("*")) {
            throw new IllegalArgumentException("API 独立发布不允许使用通配 Path");
        }
    }

    private void addMatch(Set<String> matchKeys, String method, String path) {
        String key = (method == null || method.isBlank() ? "*" : method.toUpperCase(Locale.ROOT)) + " " + path;
        if (!matchKeys.add(key)) {
            throw new IllegalArgumentException("存在重复路由匹配：" + key);
        }
    }

    private GatewayRoute baseRoute(String id, String uri, int order) {
        GatewayRoute route = new GatewayRoute();
        route.setId(id.replaceAll("[^A-Za-z0-9._-]", "-"));
        route.setUri(uri);
        route.setOrder(order);
        return route;
    }

    private GatewayRouteDefinition definition(String name, String key, String value) {
        return definition(name, Map.of(key, value));
    }

    private GatewayRouteDefinition definition(String name, Map<String, String> args) {
        GatewayRouteDefinition definition = new GatewayRouteDefinition();
        definition.setName(name);
        definition.setArgs(args);
        return definition;
    }

    /** 绑定一个 API 资产和它的发布声明，避免编译阶段重新推断关系。 */
    public record ApiPublicationCandidate(GatewayApi api, GatewayApiPublication publication) {
    }
}
