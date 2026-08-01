package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotBlank;

/** 回滚提交时读取到的当前 Nacos 版本，用于 CAS 冲突保护。 */
public record GatewayRollbackRequest(@NotBlank String baseVersion) {
}
