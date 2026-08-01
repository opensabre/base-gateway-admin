package io.github.opensabre.gateway.admin.publication.model;

/** 单个网关实例对本次发布的加载状态。 */
public enum GatewayInstanceRevisionStatus {
    LOADED,
    PENDING,
    UNREACHABLE
}
