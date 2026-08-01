package io.github.opensabre.gateway.admin.api.model;

/** 一次成功 API 同步的变更统计。 */
public record ApiSyncResult(String serviceId, int discovered, int created, int updated, int missing) {
}
