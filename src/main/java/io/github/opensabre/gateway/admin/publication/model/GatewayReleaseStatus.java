package io.github.opensabre.gateway.admin.publication.model;

/** 网关发布执行状态。 */
public enum GatewayReleaseStatus {
    PUBLISHING,
    SUCCEEDED,
    PARTIALLY_APPLIED,
    FAILED,
    RECONCILIATION_REQUIRED
}
