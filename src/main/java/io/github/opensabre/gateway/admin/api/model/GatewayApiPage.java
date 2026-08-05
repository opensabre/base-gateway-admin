package io.github.opensabre.gateway.admin.api.model;

import java.util.List;

/** API 资产分页结果。 */
public record GatewayApiPage(long total, long page, long pageSize, List<GatewayApi> apis) {
}
