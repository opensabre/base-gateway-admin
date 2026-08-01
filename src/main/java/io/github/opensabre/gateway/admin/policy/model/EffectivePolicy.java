package io.github.opensabre.gateway.admin.policy.model;

/** 解析后的策略及其来源。 */
public record EffectivePolicy(
        PolicyType policyType,
        PolicyMode effectiveMode,
        Object effectiveConfig,
        PolicyScopeType sourceScope,
        String sourceId,
        Integer sourceVersion) {
}
