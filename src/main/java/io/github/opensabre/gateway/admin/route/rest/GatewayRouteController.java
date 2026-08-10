package io.github.opensabre.gateway.admin.route.rest;

import io.github.opensabre.gateway.admin.route.model.GatewayRouteConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2ClientChange;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import io.github.opensabre.gateway.admin.integration.GatewayIntegrationProperties;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/**
 * 网关路由配置查询接口。
 */
@Tag(name = "网关路由")
@RestController
@RequestMapping("/routes")
public class GatewayRouteController {

    private final IGatewayRouteConfigService gatewayRouteConfigService;
    private final GatewayIntegrationProperties gatewayIntegrationProperties;

    public GatewayRouteController(IGatewayRouteConfigService gatewayRouteConfigService,
            GatewayIntegrationProperties gatewayIntegrationProperties) {
        this.gatewayRouteConfigService = gatewayRouteConfigService;
        this.gatewayIntegrationProperties = gatewayIntegrationProperties;
    }

    /**
     * 查询配置中心中当前生效的显式路由。
     *
     * @return 配置版本和路由列表
     */
    @GetMapping
    @Operation(summary = "查询网关路由配置")
    public GatewayRouteConfig getCurrentConfig() {
        return gatewayRouteConfigService.getCurrentConfig();
    }

    /** 显式发布网关 OAuth2/OIDC 登录认证方式，不会修改授权服务的客户端。 */
    @PutMapping("/oauth2-clients")
    @Operation(summary = "更新并发布网关 OAuth2 认证方式")
    @Audit(operationType = OperationType.UPDATE, description = "更新并发布网关 OAuth2 认证方式", module = "GATEWAY_OAUTH2_CLIENT", response = true)
    public GatewayRouteConfig updateOauth2Clients(@Valid @RequestBody GatewayOauth2ClientChange change) {
        requireWriteEnabled();
        return gatewayRouteConfigService.updateOauth2Clients(change);
    }

    /** 紧急停写开关，避免故障期间继续修改 Nacos 中的运行时配置。 */
    private void requireWriteEnabled() {
        if (!gatewayIntegrationProperties.isConfigurationWriteEnabled()) {
            throw new IllegalStateException("网关配置写入已被管理员停用");
        }
    }
}
