package io.github.opensabre.gateway.admin.service.model;

import java.util.Map;

/** 网关可路由的服务实例状态。 */
public record GatewayServiceInstance(String ip, int port, String cluster, boolean healthy,
                                     boolean enabled, double weight, Map<String, String> metadata) {
}
