package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;

import java.util.List;

/** API 发布草稿变更。 */
public record ApiPublicationChange(
        @NotBlank String externalPath,
        String upstreamPath,
        @NotNull AuthMode authMode,
        String resourceId,
        List<GatewayRouteDefinition> filters,
        Integer lockVersion) {
}
