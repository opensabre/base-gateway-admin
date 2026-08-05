package io.github.opensabre.gateway.admin.route.service.impl;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteChange;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import io.github.opensabre.gateway.admin.route.model.GatewayDefaultFilterChange;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2Client;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2ClientChange;
import io.github.opensabre.gateway.admin.route.model.GatewayManagedPublishResult;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.jasypt.encryption.StringEncryptor;
import jakarta.annotation.Resource;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从 Nacos 读取 base-gateway.yml，并提取显式网关路由。
 */
@Service
public class GatewayRouteConfigService implements IGatewayRouteConfigService {

    private static final Pattern ROUTE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Set<String> SUPPORTED_PREDICATES = Set.of(
            "Path", "Host", "Method", "Header", "Query", "RemoteAddr", "After", "Before", "Between");
    private static final Set<String> SUPPORTED_FILTERS = Set.of(
            "StripPrefix", "PrefixPath", "RewritePath", "SetPath", "AddRequestHeader", "AddResponseHeader",
            "RemoveRequestHeader", "RemoveResponseHeader", "Retry", "CircuitBreaker", "RequestRateLimiter");
    private static final Set<String> SUPPORTED_DEFAULT_FILTERS = Set.of(
            "TokenRelay", "AddRequestHeader", "AddResponseHeader", "RemoveRequestHeader", "RemoveResponseHeader",
            "Retry", "CircuitBreaker", "RequestRateLimiter");

    private static final long CONFIG_READ_TIMEOUT_MS = 10_000L;

    private final ConfigService configService;
    private final String dataId;
    private final String group;

    @Resource
    private StringEncryptor stringEncryptor;

    public GatewayRouteConfigService(
            NacosConfigManager nacosConfigManager,
            @Value("${opensabre.gateway-admin.nacos.gateway-data-id:base-gateway.yml}") String dataId,
            @Value("${opensabre.gateway-admin.nacos.group:DEFAULT_GROUP}") String group) {
        this.configService = nacosConfigManager.getConfigService();
        this.dataId = dataId;
        this.group = group;
    }

    @Override
    public GatewayRouteConfig getCurrentConfig() {
        String content = readConfigContent();
        GatewayRouteConfig config = new GatewayRouteConfig();
        config.setVersion(md5(content));
        config.setRoutes(parseRoutes(content));
        config.setDefaultFilters(parseDefaultFilters(content));
        config.setOauth2Clients(maskSecrets(parseOauth2Clients(content)));
        return config;
    }

    @Override
    public GatewayRouteConfig create(GatewayRouteChange change) {
        return changeRoutes(change.getBaseVersion(), routes -> {
            GatewayRoute route = change.getRoute();
            validateRoute(route);
            if (routes.stream().anyMatch(item -> item.getId().equals(route.getId()))) {
                throw new IllegalArgumentException("路由 ID 已存在：" + route.getId());
            }
            routes.add(route);
        });
    }

    @Override
    public GatewayRouteConfig update(String routeId, GatewayRouteChange change) {
        return changeRoutes(change.getBaseVersion(), routes -> {
            GatewayRoute route = change.getRoute();
            validateRoute(route);
            int index = findRouteIndex(routes, routeId);
            if (index < 0) {
                throw new IllegalArgumentException("路由不存在：" + routeId);
            }
            if (!routeId.equals(route.getId()) && routes.stream().anyMatch(item -> item.getId().equals(route.getId()))) {
                throw new IllegalArgumentException("路由 ID 已存在：" + route.getId());
            }
            routes.set(index, route);
        });
    }

    @Override
    public GatewayRouteConfig delete(String routeId, String baseVersion) {
        return changeRoutes(baseVersion, routes -> {
            int index = findRouteIndex(routes, routeId);
            if (index < 0) {
                throw new IllegalArgumentException("路由不存在：" + routeId);
            }
            routes.remove(index);
        });
    }

    @Override
    public GatewayRouteConfig updateDefaultFilters(GatewayDefaultFilterChange change) {
        String content = readConfigContent();
        String currentVersion = md5(content);
        if (!currentVersion.equals(change.getBaseVersion())) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
        }
        validateDefaultFilters(change.getDefaultFilters());
        String updatedContent = replaceDefaultFilters(content, change.getDefaultFilters());
        publishConfig(updatedContent, currentVersion);
        GatewayRouteConfig result = new GatewayRouteConfig();
        result.setVersion(md5(updatedContent));
        result.setRoutes(parseRoutes(updatedContent));
        result.setDefaultFilters(change.getDefaultFilters());
        return result;
    }

    @Override
    public GatewayRouteConfig updateOauth2Clients(GatewayOauth2ClientChange change) {
        String content = readConfigContent();
        String currentVersion = md5(content);
        if (!currentVersion.equals(change.getBaseVersion())) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
        }
        validateOauth2Clients(change.getClients());
        List<GatewayOauth2Client> merged = mergeSecrets(change.getClients(), parseOauth2Clients(content));
        String updatedContent = replaceOauth2Clients(content, merged);
        publishConfig(updatedContent, currentVersion);
        GatewayRouteConfig result = new GatewayRouteConfig();
        result.setVersion(md5(updatedContent));
        result.setRoutes(parseRoutes(updatedContent));
        result.setDefaultFilters(parseDefaultFilters(updatedContent));
        result.setOauth2Clients(maskSecrets(merged));
        return result;
    }

    /**
     * 控制面只拥有 api-/application- 路由和 route-api-/route-application- 熔断器实例；
     * 其余配置作为非托管内容原样保留语义。
     */
    @Override
    public GatewayManagedPublishResult publishManaged(String baseVersion, String revision,
            List<GatewayRoute> managedRoutes,
            Map<String, Map<String, Object>> circuitBreakerInstances) {
        String content = readConfigContent();
        String currentVersion = md5(content);
        if (!currentVersion.equals(baseVersion)) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
        }
        managedRoutes.forEach(GatewayRouteConfigService::validateRoute);
        List<GatewayRoute> merged = new ArrayList<>(parseRoutes(content).stream()
                .filter(route -> !isManagedRoute(route.getId())).toList());
        Set<String> ids = new java.util.HashSet<>();
        merged.forEach(route -> ids.add(route.getId()));
        for (GatewayRoute route : managedRoutes) {
            if (!ids.add(route.getId())) {
                throw new IllegalArgumentException("托管路由 ID 与现有路由冲突：" + route.getId());
            }
            merged.add(route);
        }
        String updated = replaceRoutes(content, merged);
        updated = replaceManagedCircuitBreakers(updated, circuitBreakerInstances);
        updated = replaceApiAccessRules(updated, managedRoutes);
        updated = replaceGatewayRevision(updated, revision);
        publishConfig(updated, currentVersion);
        return new GatewayManagedPublishResult(currentVersion, md5(updated), updated);
    }

    /** 历史快照回滚仍通过当前 Nacos MD5 执行 CAS，不绕过并发保护。 */
    @Override
    public GatewayManagedPublishResult publishSnapshot(String baseVersion, String revision, String snapshotContent) {
        if (snapshotContent == null || snapshotContent.isBlank()) {
            throw new IllegalArgumentException("历史网关配置快照不能为空");
        }
        // 至少解析一次并确认快照包含合法的 YAML 根节点，避免发布损坏的历史数据。
        Object loaded = new Yaml().load(snapshotContent);
        if (!(loaded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("历史网关配置快照不是有效的 YAML 对象");
        }
        String content = readConfigContent();
        String currentVersion = md5(content);
        if (!currentVersion.equals(baseVersion)) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
        }
        String updated = replaceGatewayRevision(snapshotContent, revision);
        publishConfig(updated, currentVersion);
        return new GatewayManagedPublishResult(currentVersion, md5(updated), updated);
    }

    @Override
    public List<String> managedRouteIds(String content) {
        return parseRoutes(content).stream().map(GatewayRoute::getId)
                .filter(GatewayRouteConfigService::isManagedRoute).toList();
    }

    /**
     * 每次发布都从 Nacos 重新读取全文，并让 Nacos 以 casMd5 执行最终比较，避免覆盖其他管理员的变更。
     */
    private GatewayRouteConfig changeRoutes(String baseVersion, RouteMutator mutator) {
        String content = readConfigContent();
        String currentVersion = md5(content);
        if (!currentVersion.equals(baseVersion)) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
        }
        List<GatewayRoute> routes = new ArrayList<>(parseRoutes(content));
        mutator.mutate(routes);
        String updatedContent = replaceRoutes(content, routes);
        publishConfig(updatedContent, currentVersion);
        GatewayRouteConfig result = new GatewayRouteConfig();
        result.setVersion(md5(updatedContent));
        result.setRoutes(routes);
        return result;
    }

    private String readConfigContent() {
        try {
            String content = configService.getConfig(dataId, group, CONFIG_READ_TIMEOUT_MS);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("网关配置不存在或为空：" + group + "/" + dataId);
            }
            return content;
        } catch (NacosException exception) {
            throw new IllegalStateException("无法连接配置中心读取网关路由", exception);
        }
    }

    /** 使用 OpenSabre starter 提供的 Nacos 客户端执行 MD5 CAS 全文发布。 */
    private void publishConfig(String content, String baseVersion) {
        try {
            if (!configService.publishConfigCas(dataId, group, content, baseVersion, "yaml")) {
                throw new IllegalStateException("网关配置已被其他人修改，请刷新后重试");
            }
        } catch (NacosException exception) {
            throw new IllegalStateException("无法连接配置中心发布网关路由", exception);
        }
    }

    @SuppressWarnings("unchecked")
    static List<GatewayRoute> parseRoutes(String content) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            return List.of();
        }
        Object spring = root.get("spring");
        Object cloud = child(spring, "cloud");
        Object gateway = child(cloud, "gateway");
        Object routeValue = child(gateway, "routes");
        if (!(routeValue instanceof List<?> routeList)) {
            return List.of();
        }
        List<GatewayRoute> routes = new ArrayList<>();
        for (Object routeValueItem : routeList) {
            if (!(routeValueItem instanceof Map<?, ?> routeMap)) {
                continue;
            }
            GatewayRoute route = new GatewayRoute();
            route.setId(stringValue(routeMap.get("id")));
            route.setUri(stringValue(routeMap.get("uri")));
            route.setOrder(numberValue(routeMap.get("order")));
            route.setPredicates(parseDefinitions(routeMap.get("predicates"), true));
            route.setFilters(parseDefinitions(routeMap.get("filters"), false));
            route.setMetadata(objectMap(routeMap.get("metadata")));
            routes.add(route);
        }
        return routes;
    }

    @SuppressWarnings("unchecked")
    static List<GatewayRouteDefinition> parseDefaultFilters(String content) {
        Object loaded = new Yaml().load(content);
        Object gateway = child(child(loaded instanceof Map<?, ?> root ? root.get("spring") : null, "cloud"), "gateway");
        return parseDefinitions(child(gateway, "default-filters"), false);
    }

    @SuppressWarnings("unchecked")
    static List<GatewayOauth2Client> parseOauth2Clients(String content) {
        Object loaded = new Yaml().load(content);
        Object registrationValue = child(child(child(child(loaded instanceof Map<?, ?> root ? root.get("spring") : null,
                "security"), "oauth2"), "client"), "registration");
        Object providerValue = child(child(child(child(loaded instanceof Map<?, ?> root ? root.get("spring") : null,
                "security"), "oauth2"), "client"), "provider");
        Object disabledValue = child(child(child(loaded instanceof Map<?, ?> root ? root.get("opensabre") : null,
                "gateway"), "oauth2"), "disabled-registration-ids");
        Set<String> disabled = disabledValue instanceof List<?> values
                ? values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet()) : Set.of();
        if (!(registrationValue instanceof Map<?, ?> registrations)) return List.of();
        List<GatewayOauth2Client> result = new ArrayList<>();
        registrations.forEach((id, value) -> {
            if (!(value instanceof Map<?, ?> registration)) return;
            GatewayOauth2Client item = new GatewayOauth2Client();
            item.setRegistrationId(String.valueOf(id));
            item.setProvider(stringValue(registration.get("provider")));
            Object provider = providerValue instanceof Map<?, ?> providers ? providers.get(item.getProvider()) : null;
            item.setIssuerUri(stringValue(child(provider, "issuer-uri")));
            item.setClientId(stringValue(registration.get("client-id")));
            item.setClientSecret(stringValue(registration.get("client-secret")));
            item.setRedirectUri(stringValue(registration.get("redirect-uri")));
            Object scopes = registration.get("scope");
            if (scopes instanceof List<?> values) item.setScopes(values.stream().map(String::valueOf).toList());
            Object enabled = registration.get("enabled");
            item.setEnabled(!disabled.contains(item.getRegistrationId()) && (enabled == null || !"false".equals(String.valueOf(enabled))));
            result.add(item);
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    static String replaceOauth2Clients(String content, List<GatewayOauth2Client> clients) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        Map<Object, Object> spring = (Map<Object, Object>) root.get("spring");
        Map<Object, Object> security = (Map<Object, Object>) spring.computeIfAbsent("security", key -> new LinkedHashMap<>());
        Map<Object, Object> oauth2 = (Map<Object, Object>) security.computeIfAbsent("oauth2", key -> new LinkedHashMap<>());
        Map<Object, Object> client = (Map<Object, Object>) oauth2.computeIfAbsent("client", key -> new LinkedHashMap<>());
        Map<Object, Object> registrations = new LinkedHashMap<>();
        Map<Object, Object> providers = new LinkedHashMap<>();
        for (GatewayOauth2Client item : clients) {
            Map<Object, Object> value = new LinkedHashMap<>();
            value.put("provider", item.getProvider()); value.put("client-id", item.getClientId());
            value.put("client-secret", item.getClientSecret()); value.put("client-authentication-method", "client_secret_basic");
            value.put("authorization-grant-type", "authorization_code"); value.put("redirect-uri", item.getRedirectUri());
            value.put("scope", item.getScopes()); registrations.put(item.getRegistrationId(), value);
            Map<Object, Object> provider = new LinkedHashMap<>();
            provider.put("issuer-uri", item.getIssuerUri());
            provider.put("user-info-uri", item.getIssuerUri() + "/userinfo");
            provider.put("user-name-attribute", "name");
            providers.put(item.getProvider(), provider);
        }
        client.put("registration", registrations);
        client.put("provider", providers);
        Map<Object, Object> rootMap = (Map<Object, Object>) root;
        Map<Object, Object> opensabre = (Map<Object, Object>) rootMap.computeIfAbsent("opensabre", key -> new LinkedHashMap<>());
        Map<Object, Object> gateway = (Map<Object, Object>) opensabre.computeIfAbsent("gateway", key -> new LinkedHashMap<>());
        Map<Object, Object> gatewayOauth2 = (Map<Object, Object>) gateway.computeIfAbsent("oauth2", key -> new LinkedHashMap<>());
        gatewayOauth2.put("disabled-registration-ids", clients.stream().filter(item -> !item.isEnabled())
                .map(GatewayOauth2Client::getRegistrationId).toList());
        return new Yaml().dump(root);
    }

    /**
     * 仅替换 spring.cloud.gateway.routes 节点；路由外的键和值保持原有语义及顺序。
     */
    @SuppressWarnings("unchecked")
    static String replaceRoutes(String content, List<GatewayRoute> routes) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        }
        Object spring = root.get("spring");
        Object cloud = child(spring, "cloud");
        Object gateway = child(cloud, "gateway");
        if (!(gateway instanceof Map<?, ?> gatewayMap)) {
            throw new IllegalStateException("网关配置缺少 spring.cloud.gateway 节点");
        }
        ((Map<Object, Object>) gatewayMap).put("routes", toRouteMaps(routes));
        return new Yaml().dump(root);
    }

    /** 仅替换 spring.cloud.gateway.default-filters，不影响 routes 及其他网关配置。 */
    @SuppressWarnings("unchecked")
    static String replaceDefaultFilters(String content, List<GatewayRouteDefinition> filters) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        Object gateway = child(child(root.get("spring"), "cloud"), "gateway");
        if (!(gateway instanceof Map<?, ?> gatewayMap)) throw new IllegalStateException("网关配置缺少 spring.cloud.gateway 节点");
        ((Map<Object, Object>) gatewayMap).put("default-filters", toDefinitionMaps(filters));
        return new Yaml().dump(root);
    }

    /** 替换控制面生成的熔断器实例，保留人工维护的其他 Resilience4j 实例。 */
    @SuppressWarnings("unchecked")
    static String replaceManagedCircuitBreakers(String content,
            Map<String, Map<String, Object>> managedInstances) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        }
        Map<Object, Object> rootMap = (Map<Object, Object>) root;
        Map<Object, Object> resilience4j = (Map<Object, Object>) rootMap.computeIfAbsent(
                "resilience4j", key -> new LinkedHashMap<>());
        Map<Object, Object> circuitBreaker = (Map<Object, Object>) resilience4j.computeIfAbsent(
                "circuitbreaker", key -> new LinkedHashMap<>());
        Map<Object, Object> instances = circuitBreaker.get("instances") instanceof Map<?, ?> values
                ? new LinkedHashMap<>((Map<Object, Object>) values) : new LinkedHashMap<>();
        instances.keySet().removeIf(key -> isManagedCircuitBreaker(String.valueOf(key)));
        managedInstances.forEach((name, config) -> instances.put(name, new LinkedHashMap<>(config)));
        circuitBreaker.put("instances", instances);
        return new Yaml().dump(root);
    }

    /** 将本次发布 ID 写入配置，实例刷新后可报告实际加载的修订号。 */
    @SuppressWarnings("unchecked")
    static String replaceGatewayRevision(String content, String revision) {
        if (revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("网关发布修订号不能为空");
        }
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        }
        Map<Object, Object> rootMap = (Map<Object, Object>) root;
        Map<Object, Object> opensabre = (Map<Object, Object>) rootMap.computeIfAbsent(
                "opensabre", key -> new LinkedHashMap<>());
        Map<Object, Object> gateway = (Map<Object, Object>) opensabre.computeIfAbsent(
                "gateway", key -> new LinkedHashMap<>());
        gateway.put("revision", revision);
        return new Yaml().dump(root);
    }

    /** 根据 API Route 的 Method、Path 和控制面鉴权元数据生成动态访问规则。 */
    @SuppressWarnings("unchecked")
    static String replaceApiAccessRules(String content, List<GatewayRoute> managedRoutes) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("网关配置不是有效的 YAML 对象");
        }
        List<Map<String, Object>> rules = new ArrayList<>();
        for (GatewayRoute route : managedRoutes) {
            if (route.getId() == null || !route.getId().startsWith("api-")) continue;
            String mode = String.valueOf(route.getMetadata().getOrDefault("opensabre-auth-mode", ""));
            String method = definitionArgument(route.getPredicates(), "Method", "method");
            String path = definitionArgument(route.getPredicates(), "Path", "pattern");
            if (mode.isBlank() || method.isBlank() || path.isBlank()) {
                throw new IllegalArgumentException("API 托管路由缺少鉴权模式、Method 或 Path：" + route.getId());
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("route-id", route.getId());
            rule.put("method", method);
            rule.put("path", path);
            rule.put("mode", mode);
            rules.add(rule);
        }
        Map<Object, Object> rootMap = (Map<Object, Object>) root;
        Map<Object, Object> opensabre = (Map<Object, Object>) rootMap.computeIfAbsent(
                "opensabre", key -> new LinkedHashMap<>());
        Map<Object, Object> gateway = (Map<Object, Object>) opensabre.computeIfAbsent(
                "gateway", key -> new LinkedHashMap<>());
        Map<Object, Object> apiAccess = (Map<Object, Object>) gateway.computeIfAbsent(
                "api-access", key -> new LinkedHashMap<>());
        apiAccess.put("rules", rules);
        return new Yaml().dump(root);
    }

    private static String definitionArgument(List<GatewayRouteDefinition> definitions, String name, String key) {
        if (definitions == null) return "";
        return definitions.stream().filter(definition -> name.equals(definition.getName())).findFirst()
                .map(definition -> definition.getArgs().getOrDefault(key, "")).orElse("");
    }

    private static boolean isManagedRoute(String id) {
        return id != null && (id.startsWith("api-") || id.startsWith("application-"));
    }

    private static boolean isManagedCircuitBreaker(String name) {
        return name.startsWith("route-api-") || name.startsWith("route-application-");
    }

    private static List<Map<String, Object>> toRouteMaps(List<GatewayRoute> routes) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GatewayRoute route : routes) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", route.getId());
            value.put("uri", route.getUri());
            value.put("order", route.getOrder());
            value.put("predicates", toDefinitionMaps(route.getPredicates()));
            value.put("filters", toDefinitionMaps(route.getFilters()));
            if (route.getMetadata() != null && !route.getMetadata().isEmpty()) {
                value.put("metadata", new LinkedHashMap<>(route.getMetadata()));
            }
            values.add(value);
        }
        return values;
    }

    private static List<Map<String, Object>> toDefinitionMaps(List<GatewayRouteDefinition> definitions) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (GatewayRouteDefinition definition : definitions) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", definition.getName());
            value.put("args", new LinkedHashMap<>(definition.getArgs()));
            values.add(value);
        }
        return values;
    }

    static void validateRoute(GatewayRoute route) {
        if (route == null || route.getId() == null || !ROUTE_ID_PATTERN.matcher(route.getId()).matches()) {
            throw new IllegalArgumentException("路由 ID 只能包含字母、数字、点、下划线和连字符，且不能以符号开头");
        }
        String uri = route.getUri();
        if (uri == null || !(uri.startsWith("lb://") || uri.startsWith("http://")
                || uri.startsWith("https://") || uri.startsWith("forward:"))) {
            throw new IllegalArgumentException("目标 URI 仅支持 lb://、http://、https:// 或 forward: 前缀");
        }
        validateDefinitions(route.getPredicates(), SUPPORTED_PREDICATES, "断言", true);
        validateDefinitions(route.getFilters(), SUPPORTED_FILTERS, "过滤器", false);
    }

    static void validateDefaultFilters(List<GatewayRouteDefinition> filters) {
        validateDefinitions(filters, SUPPORTED_DEFAULT_FILTERS, "全局过滤器", false);
        boolean tokenRelayEnabled = filters.stream()
                .anyMatch(filter -> "TokenRelay".equals(filter.getName()));
        if (!tokenRelayEnabled) {
            throw new IllegalArgumentException("全局过滤器必须保留 TokenRelay，否则 OAuth2 登录态无法转发到后端服务");
        }
        for (GatewayRouteDefinition filter : filters) {
            if ("RequestRateLimiter".equals(filter.getName())) {
                Map<String, String> args = filter.getArgs();
                int replenish = parsePositive(args.get("redis-rate-limiter.replenishRate"), "限流补充速率");
                int burst = parsePositive(args.get("redis-rate-limiter.burstCapacity"), "限流突发容量");
                if (burst < replenish) throw new IllegalArgumentException("限流突发容量不能小于补充速率");
                if (!"#{@defaultRedisRateLimiter}".equals(args.get("rate-limiter"))) throw new IllegalArgumentException("限流器必须使用 defaultRedisRateLimiter");
                String keyResolver = args.get("key-resolver");
                if (!Set.of("#{@remoteAddressKeyResolver}", "#{@apiKeyResolver}").contains(keyResolver)) throw new IllegalArgumentException("限流 Key Resolver 仅支持 IP 或请求路径");
            }
        }
    }

    static void validateOauth2Clients(List<GatewayOauth2Client> clients) {
        Set<String> ids = new java.util.HashSet<>();
        Map<String, String> issuers = new LinkedHashMap<>();
        for (GatewayOauth2Client client : clients) {
            if (client == null || client.getRegistrationId() == null
                    || !ROUTE_ID_PATTERN.matcher(client.getRegistrationId()).matches()) {
                throw new IllegalArgumentException("OAuth2 注册名格式不正确");
            }
            if (!ids.add(client.getRegistrationId())) throw new IllegalArgumentException("OAuth2 注册名不能重复");
            if (isBlank(client.getProvider()) || isBlank(client.getIssuerUri()) || isBlank(client.getClientId()) || isBlank(client.getRedirectUri())) {
                throw new IllegalArgumentException("OAuth2 Provider、Issuer URI、客户端 ID 和回调地址不能为空");
            }
            if (!(client.getIssuerUri().startsWith("http://") || client.getIssuerUri().startsWith("https://"))) throw new IllegalArgumentException("Issuer URI 必须是 HTTP(S) 地址");
            String previousIssuer = issuers.putIfAbsent(client.getProvider(), client.getIssuerUri());
            if (previousIssuer != null && !previousIssuer.equals(client.getIssuerUri())) {
                throw new IllegalArgumentException("同一 Provider 只能配置一个 Issuer URI");
            }
            // opensabre 是当前非 TLS 集成环境的受控入口；其他 HTTP 回调仍禁止，避免误配为不安全的外部地址。
            if (!(client.getRedirectUri().startsWith("http://localhost:")
                    || client.getRedirectUri().startsWith("http://opensabre:")
                    || client.getRedirectUri().startsWith("https://"))) {
                throw new IllegalArgumentException("回调地址仅支持 HTTPS、localhost 或 opensabre 测试入口");
            }
            if (client.getScopes() == null || client.getScopes().isEmpty()) {
                throw new IllegalArgumentException("OAuth2 作用域不能为空");
            }
        }
    }

    private List<GatewayOauth2Client> mergeSecrets(List<GatewayOauth2Client> clients,
            List<GatewayOauth2Client> current) {
        Map<String, String> currentSecrets = new LinkedHashMap<>();
        current.forEach(item -> currentSecrets.put(item.getRegistrationId(), item.getClientSecret()));
        List<GatewayOauth2Client> result = new ArrayList<>();
        for (GatewayOauth2Client client : clients) {
            if (isBlank(client.getClientSecret())) client.setClientSecret(currentSecrets.get(client.getRegistrationId()));
            if (client.isEnabled() && isBlank(client.getClientSecret())) {
                throw new IllegalArgumentException("启用的 OAuth2 认证方式必须设置客户端密钥");
            }
            if (!isBlank(client.getClientSecret()) && !client.getClientSecret().startsWith("ENC(")
                    && !client.getClientSecret().startsWith("${")) {
                client.setClientSecret("ENC(" + stringEncryptor.encrypt(client.getClientSecret()) + ")");
            }
            result.add(client);
        }
        return result;
    }

    private static List<GatewayOauth2Client> maskSecrets(List<GatewayOauth2Client> clients) {
        List<GatewayOauth2Client> result = new ArrayList<>();
        for (GatewayOauth2Client source : clients) {
            GatewayOauth2Client target = new GatewayOauth2Client();
            target.setRegistrationId(source.getRegistrationId()); target.setProvider(source.getProvider());
            target.setIssuerUri(source.getIssuerUri());
            target.setClientId(source.getClientId()); target.setRedirectUri(source.getRedirectUri());
            target.setScopes(new ArrayList<>(source.getScopes())); target.setEnabled(source.isEnabled());
            target.setClientSecret(isBlank(source.getClientSecret()) ? "" : "******");
            result.add(target);
        }
        return result;
    }

    private static int parsePositive(String value, String field) {
        try { int parsed = Integer.parseInt(value); if (parsed > 0 && parsed <= 100000) return parsed; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(field + "必须是 1 到 100000 的整数");
    }

    private static void validateDefinitions(List<GatewayRouteDefinition> definitions, Set<String> allowed, String type,
                                            boolean required) {
        if (required && (definitions == null || definitions.isEmpty())) {
            throw new IllegalArgumentException("路由至少需要一个断言");
        }
        if (definitions == null) {
            return;
        }
        for (GatewayRouteDefinition definition : definitions) {
            if (definition == null || definition.getName() == null || !allowed.contains(definition.getName())) {
                throw new IllegalArgumentException("不支持的" + type + "：" + (definition == null ? "" : definition.getName()));
            }
            boolean argumentlessTokenRelay = "TokenRelay".equals(definition.getName())
                    && (definition.getArgs() == null || definition.getArgs().isEmpty());
            if (!argumentlessTokenRelay && (definition.getArgs() == null || definition.getArgs().isEmpty()
                    || definition.getArgs().entrySet().stream()
                    .anyMatch(item -> isBlank(item.getKey()) || isBlank(item.getValue())))) {
                throw new IllegalArgumentException(type + " " + definition.getName() + " 的参数不能为空");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int findRouteIndex(List<GatewayRoute> routes, String routeId) {
        for (int index = 0; index < routes.size(); index++) {
            if (routes.get(index).getId().equals(routeId)) {
                return index;
            }
        }
        return -1;
    }

    @FunctionalInterface
    private interface RouteMutator {
        void mutate(List<GatewayRoute> routes);
    }

    private static Object child(Object parent, String name) {
        return parent instanceof Map<?, ?> values ? values.get(name) : null;
    }

    private static List<GatewayRouteDefinition> parseDefinitions(Object values, boolean predicate) {
        if (!(values instanceof List<?> definitions)) {
            return List.of();
        }
        List<GatewayRouteDefinition> result = new ArrayList<>();
        for (Object definition : definitions) {
            GatewayRouteDefinition routeDefinition = new GatewayRouteDefinition();
            if (definition instanceof String text) {
                int separator = text.indexOf('=');
                routeDefinition.setName(separator < 0 ? text : text.substring(0, separator));
                if (separator >= 0) {
                    String key = predicate && "Path".equals(routeDefinition.getName()) ? "pattern" : "value";
                    routeDefinition.setArgs(Map.of(key, text.substring(separator + 1)));
                }
            } else if (definition instanceof Map<?, ?> map) {
                routeDefinition.setName(stringValue(map.get("name")));
                routeDefinition.setArgs(stringArgs(map.get("args")));
            } else {
                continue;
            }
            result.add(routeDefinition);
        }
        return result;
    }

    private static Map<String, String> stringArgs(Object value) {
        if (!(value instanceof Map<?, ?> args)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        args.forEach((key, item) -> result.put(String.valueOf(key), stringValue(item)));
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String md5(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 算法不可用", exception);
        }
    }
}
