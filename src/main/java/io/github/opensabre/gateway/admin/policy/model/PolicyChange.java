package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 类型化策略变更。仅与 policyType 对应的配置字段允许有值。
 */
public record PolicyChange(
        @NotNull PolicyMode mode,
        @Valid RateLimitPolicyConfig rateLimit,
        @Valid TimeoutPolicyConfig timeout,
        @Valid CircuitBreakerPolicyConfig circuitBreaker,
        @Valid AccessControlPolicyConfig accessControl,
        @Valid SecurityHeadersPolicyConfig securityHeaders,
        @Valid DefaultFiltersPolicyConfig defaultFilters,
        @Valid CorsPolicyConfig cors,
        Integer lockVersion) {
}
