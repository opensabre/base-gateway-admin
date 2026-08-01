package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 限流策略参数。 */
public record RateLimitPolicyConfig(
        @NotNull KeyType keyType,
        @Min(1) int replenishRate,
        @Min(1) int burstCapacity,
        @Min(1) int requestedTokens) {

    /** 首批支持的限流键。 */
    public enum KeyType {
        IP,
        USER,
        OAUTH_CLIENT,
        API
    }
}
