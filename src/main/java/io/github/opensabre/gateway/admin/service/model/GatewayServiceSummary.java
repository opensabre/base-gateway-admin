package io.github.opensabre.gateway.admin.service.model;

import java.util.List;

/** 服务及其当前注册实例摘要。 */
public record GatewayServiceSummary(String name, int instanceCount, int healthyInstanceCount,
                                    List<GatewayServiceInstance> instances) {
}
