package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotBlank;

/** 将当前 Nacos 中的非托管路由导入为应用路由草稿。 */
public record LegacyRouteAdoptionRequest(@NotBlank String routeId, @NotBlank String baseVersion) {
}
