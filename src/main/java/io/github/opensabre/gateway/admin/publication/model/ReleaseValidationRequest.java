package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotBlank;

/** 发布预检使用的 Nacos 配置基线。 */
public record ReleaseValidationRequest(@NotBlank String baseVersion) {
}
