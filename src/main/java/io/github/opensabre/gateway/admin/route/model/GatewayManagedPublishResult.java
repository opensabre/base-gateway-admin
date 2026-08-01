package io.github.opensabre.gateway.admin.route.model;

/** Nacos CAS 发布后的不可变配置快照。 */
public record GatewayManagedPublishResult(String sourceVersion, String targetVersion, String content) {
}
