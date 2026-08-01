package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/** 熔断策略参数。 */
public record CircuitBreakerPolicyConfig(
        @DecimalMin("1.0") @DecimalMax("100.0") double failureRateThreshold,
        @DecimalMin("1.0") @DecimalMax("100.0") double slowCallRateThreshold,
        @Min(1) long slowCallDurationThresholdMs,
        @Min(1) int minimumNumberOfCalls,
        @Min(1) long waitDurationInOpenStateMs,
        String fallbackUri) {
}
