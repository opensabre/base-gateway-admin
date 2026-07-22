package io.github.opensabre.gateway.admin.service.model;

import java.util.List;

/** Nacos 服务目录分页结果。 */
public record GatewayServicePage(long total, int page, int pageSize, List<GatewayServiceSummary> services) {
}
