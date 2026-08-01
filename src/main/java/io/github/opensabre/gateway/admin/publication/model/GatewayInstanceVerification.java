package io.github.opensabre.gateway.admin.publication.model;

import java.util.List;

/** 发布后逐实例加载确认汇总。 */
public record GatewayInstanceVerification(int total, int loaded, List<GatewayInstanceRevision> instances) {
    public boolean allLoaded() {
        return total > 0 && total == loaded;
    }
}
