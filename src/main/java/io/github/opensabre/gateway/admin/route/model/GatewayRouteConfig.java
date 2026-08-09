package io.github.opensabre.gateway.admin.route.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关路由配置中心快照。
 */
public class GatewayRouteConfig {

    private String version;
    private List<GatewayRoute> routes = new ArrayList<>();
    /** 应用于所有显式路由的 Spring Cloud Gateway default-filters。 */
    private List<GatewayRouteDefinition> defaultFilters = new ArrayList<>();
    /** 网关 OAuth2/OIDC 登录认证方式，密钥在 API 响应中已脱敏。 */
    private List<GatewayOauth2Client> oauth2Clients = new ArrayList<>();
    private Map<String, Map<String, Object>> globalCorsConfigurations = new LinkedHashMap<>();
    private boolean corsAddToSimpleUrlHandlerMapping;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<GatewayRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<GatewayRoute> routes) {
        this.routes = routes;
    }

    public List<GatewayRouteDefinition> getDefaultFilters() { return defaultFilters; }
    public void setDefaultFilters(List<GatewayRouteDefinition> defaultFilters) { this.defaultFilters = defaultFilters; }
    public List<GatewayOauth2Client> getOauth2Clients() { return oauth2Clients; }
    public void setOauth2Clients(List<GatewayOauth2Client> oauth2Clients) { this.oauth2Clients = oauth2Clients; }
    public Map<String, Map<String, Object>> getGlobalCorsConfigurations() { return globalCorsConfigurations; }
    public void setGlobalCorsConfigurations(Map<String, Map<String, Object>> configurations) {
        this.globalCorsConfigurations = configurations;
    }
    public boolean isCorsAddToSimpleUrlHandlerMapping() { return corsAddToSimpleUrlHandlerMapping; }
    public void setCorsAddToSimpleUrlHandlerMapping(boolean value) { this.corsAddToSimpleUrlHandlerMapping = value; }
}
