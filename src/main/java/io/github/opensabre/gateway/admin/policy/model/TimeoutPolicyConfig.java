package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.constraints.Min;

/** 超时策略参数，单位为毫秒。 */
public record TimeoutPolicyConfig(
        @Min(1) int connectTimeoutMs,
        @Min(1) int responseTimeoutMs) {
}
