package io.github.opensabre.gateway.admin.rest;

import io.github.opensabre.gateway.admin.integration.GatewayIntegrationProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 网关控制面能力边界说明接口。
 */
@Tag(name = "网关控制面")
@RestController
@RequestMapping("/info")
public class GatewayAdminInfoController {

    private final GatewayIntegrationProperties properties;

    public GatewayAdminInfoController(GatewayIntegrationProperties properties) {
        this.properties = properties;
    }

    /** 返回当前迭代已经启用的控制面能力。 */
    @GetMapping
    @Operation(summary = "查询网关控制面能力")
    public Map<String, Object> info() {
        return Map.of(
                "application", "base-gateway-admin",
                "phase", "foundation",
                "configurationWriteEnabled", properties.isConfigurationWriteEnabled(),
                "capabilities", new String[]{"nacos-read", "prometheus-read"});
    }
}
