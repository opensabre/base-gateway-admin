package io.github.opensabre.gateway.admin.publication.model;

/** 正式发布结果。 */
public record GatewayReleaseResult(String releaseId, String sourceVersion, String targetVersion,
                                   int apiCount, int applicationRouteCount,
                                   GatewayReleaseStatus status) {
}
