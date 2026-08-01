package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotBlank;

/** 应用级路由草稿变更。 */
public record ApplicationRouteChange(
        @NotBlank String serviceId,
        @NotBlank String routeName,
        @NotBlank String externalPath,
        @NotBlank String targetUri,
        String httpMethod,
        String rewritePath,
        Integer lockVersion) {
}
