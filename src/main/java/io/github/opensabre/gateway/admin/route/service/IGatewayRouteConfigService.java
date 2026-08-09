package io.github.opensabre.gateway.admin.route.service;

import io.github.opensabre.gateway.admin.route.model.GatewayRouteConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteChange;
import io.github.opensabre.gateway.admin.route.model.GatewayDefaultFilterChange;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2ClientChange;
import io.github.opensabre.gateway.admin.route.model.GatewayManagedPublishResult;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.policy.service.GlobalRuleCompilation;

import java.util.List;
import java.util.Map;

/**
 * 读取配置中心中的网关路由。
 */
public interface IGatewayRouteConfigService {

    /**
     * 获取当前 Nacos 配置版本及其显式路由。
     *
     * @return 路由配置快照
     */
    GatewayRouteConfig getCurrentConfig();

    /** 将一条新增或修改后的路由发布到 Nacos。 */
    GatewayRouteConfig create(GatewayRouteChange change);

    /** 将指定路由替换为新定义并发布到 Nacos。 */
    GatewayRouteConfig update(String routeId, GatewayRouteChange change);

    /** 使用调用方读取到的版本删除一条路由并发布到 Nacos。 */
    GatewayRouteConfig delete(String routeId, String baseVersion);

    /** 替换 Nacos 中的 default-filters，并用版本号保护并发发布。 */
    GatewayRouteConfig updateDefaultFilters(GatewayDefaultFilterChange change);
    /** 发布网关 OAuth2/OIDC 登录认证方式。 */
    GatewayRouteConfig updateOauth2Clients(GatewayOauth2ClientChange change);

    /** 原子替换控制面托管路由及其 Resilience4j 实例，保留其他配置。 */
    GatewayManagedPublishResult publishManaged(String baseVersion, String revision, List<GatewayRoute> managedRoutes,
            Map<String, Map<String, Object>> circuitBreakerInstances, GlobalRuleCompilation globalRules);

    /** 使用当前版本 CAS 发布一个已保存的完整历史快照。 */
    GatewayManagedPublishResult publishSnapshot(String baseVersion, String revision, String snapshotContent);

    /** 从完整配置中提取控制面托管 Route ID。 */
    List<String> managedRouteIds(String content);
}
