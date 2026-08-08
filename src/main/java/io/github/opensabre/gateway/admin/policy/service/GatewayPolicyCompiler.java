package io.github.opensabre.gateway.admin.policy.service;

import io.github.opensabre.gateway.admin.policy.model.AccessControlPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.CircuitBreakerPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.model.RateLimitPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.TimeoutPolicyConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将已解析的 Effective Policy 编译为单份路由治理配置。 */
@Component
public class GatewayPolicyCompiler {

    /**
     * 同一种策略最多接收一份最终结果，避免三级配置在运行时叠加执行。
     */
    public PolicyCompilation apply(GatewayRoute route, List<EffectivePolicy> policies) {
        Map<PolicyType, EffectivePolicy> unique = new LinkedHashMap<>();
        for (EffectivePolicy policy : policies) {
            if (unique.putIfAbsent(policy.policyType(), policy) != null) {
                throw new IllegalArgumentException("Effective Policy 不能包含重复策略：" + policy.policyType());
            }
        }
        List<GatewayRouteDefinition> filters = new ArrayList<>(route.getFilters());
        Map<String, Object> metadata = new LinkedHashMap<>(route.getMetadata());
        Map<String, Map<String, Object>> circuitBreakerInstances = new LinkedHashMap<>();
        compileRateLimit(unique.get(PolicyType.RATE_LIMIT), filters);
        compileTimeout(unique.get(PolicyType.TIMEOUT), metadata);
        compileCircuitBreaker(route.getId(), unique.get(PolicyType.CIRCUIT_BREAKER),
                filters, circuitBreakerInstances);
        compileAccessControl(unique.get(PolicyType.ACCESS_CONTROL), filters);
        route.setFilters(List.copyOf(filters));
        route.setMetadata(Map.copyOf(metadata));
        return new PolicyCompilation(route, Map.copyOf(circuitBreakerInstances));
    }

    private void compileAccessControl(EffectivePolicy policy, List<GatewayRouteDefinition> filters) {
        if (!enabled(policy)) return;
        AccessControlPolicyConfig config = requireConfig(policy, AccessControlPolicyConfig.class);
        Map<String, String> args = new LinkedHashMap<>();
        args.put("mode", config.accessMode().name());
        args.put("cidrs", config.entries().stream().map(AccessControlPolicyConfig.Entry::cidr)
                .map(String::trim).distinct().collect(java.util.stream.Collectors.joining(",")));
        filters.add(definition("OpenSabreIpAccessControl", args));
    }

    private void compileRateLimit(EffectivePolicy policy, List<GatewayRouteDefinition> filters) {
        if (!enabled(policy)) return;
        RateLimitPolicyConfig config = requireConfig(policy, RateLimitPolicyConfig.class);
        if (config.burstCapacity() < config.replenishRate()) {
            throw new IllegalArgumentException("限流 burstCapacity 不能小于 replenishRate");
        }
        String resolver = switch (config.keyType()) {
            case IP -> "#{@remoteAddressKeyResolver}";
            case API -> "#{@apiKeyResolver}";
            case USER, OAUTH_CLIENT -> throw new IllegalArgumentException(
                    "当前网关尚未提供可信的 " + config.keyType() + " KeyResolver");
        };
        Map<String, String> args = new LinkedHashMap<>();
        args.put("redis-rate-limiter.replenishRate", String.valueOf(config.replenishRate()));
        args.put("redis-rate-limiter.burstCapacity", String.valueOf(config.burstCapacity()));
        args.put("redis-rate-limiter.requestedTokens", String.valueOf(config.requestedTokens()));
        args.put("rate-limiter", "#{@defaultRedisRateLimiter}");
        args.put("key-resolver", resolver);
        filters.add(definition("RequestRateLimiter", args));
    }

    private void compileTimeout(EffectivePolicy policy, Map<String, Object> metadata) {
        if (!enabled(policy)) return;
        TimeoutPolicyConfig config = requireConfig(policy, TimeoutPolicyConfig.class);
        // Gateway 4.2 NettyRoutingFilter directly reads these two route metadata keys as milliseconds.
        metadata.put("connect-timeout", config.connectTimeoutMs());
        metadata.put("response-timeout", config.responseTimeoutMs());
    }

    private void compileCircuitBreaker(String routeId, EffectivePolicy policy,
            List<GatewayRouteDefinition> filters,
            Map<String, Map<String, Object>> instances) {
        if (!enabled(policy)) return;
        CircuitBreakerPolicyConfig config = requireConfig(policy, CircuitBreakerPolicyConfig.class);
        String instanceName = "route-" + routeId.replaceAll("[^A-Za-z0-9_-]", "-");
        Map<String, String> filterArgs = new LinkedHashMap<>();
        filterArgs.put("name", instanceName);
        if (config.fallbackUri() != null && !config.fallbackUri().isBlank()) {
            if (!config.fallbackUri().startsWith("forward:/")) {
                throw new IllegalArgumentException("熔断 fallbackUri 当前仅支持 forward:/ 内部地址");
            }
            filterArgs.put("fallbackUri", config.fallbackUri());
        }
        filters.add(definition("CircuitBreaker", filterArgs));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("failureRateThreshold", config.failureRateThreshold());
        properties.put("slowCallRateThreshold", config.slowCallRateThreshold());
        properties.put("slowCallDurationThreshold", config.slowCallDurationThresholdMs() + "ms");
        properties.put("minimumNumberOfCalls", config.minimumNumberOfCalls());
        properties.put("waitDurationInOpenState", config.waitDurationInOpenStateMs() + "ms");
        instances.put(instanceName, Map.copyOf(properties));
    }

    private boolean enabled(EffectivePolicy policy) {
        return policy != null && policy.effectiveMode() == PolicyMode.ENABLED;
    }

    private <T> T requireConfig(EffectivePolicy policy, Class<T> type) {
        if (!type.isInstance(policy.effectiveConfig())) {
            throw new IllegalArgumentException(policy.policyType() + " Effective Policy 配置类型不正确");
        }
        return type.cast(policy.effectiveConfig());
    }

    private GatewayRouteDefinition definition(String name, Map<String, String> args) {
        GatewayRouteDefinition definition = new GatewayRouteDefinition();
        definition.setName(name);
        definition.setArgs(args);
        return definition;
    }

    /** 路由和需要写入 resilience4j.circuitbreaker.instances 的实例配置。 */
    public record PolicyCompilation(GatewayRoute route,
                                    Map<String, Map<String, Object>> circuitBreakerInstances) {
    }
}
