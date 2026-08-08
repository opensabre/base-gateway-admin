package io.github.opensabre.gateway.admin.policy.service;

import io.github.opensabre.gateway.admin.policy.model.AccessControlPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.CircuitBreakerPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.PolicyScopeType;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.model.RateLimitPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.TimeoutPolicyConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayPolicyCompilerTest {

    private final GatewayPolicyCompiler compiler = new GatewayPolicyCompiler();

    @Test
    void compilesOneFilterPerEnabledPolicyAndTimeoutMetadata() {
        GatewayRoute route = route();
        var result = compiler.apply(route, List.of(
                effective(PolicyType.RATE_LIMIT,
                        new RateLimitPolicyConfig(RateLimitPolicyConfig.KeyType.IP, 10, 20, 1)),
                effective(PolicyType.TIMEOUT, new TimeoutPolicyConfig(500, 2000)),
                effective(PolicyType.CIRCUIT_BREAKER,
                        new CircuitBreakerPolicyConfig(50, 60, 1000, 10, 5000, "forward:/fallback")),
                effective(PolicyType.ACCESS_CONTROL, new AccessControlPolicyConfig(
                        AccessControlPolicyConfig.AccessMode.DENYLIST,
                        List.of(new AccessControlPolicyConfig.Entry("10.0.0.0/8", "internal"))))));

        assertThat(result.route().getFilters()).extracting("name")
                .containsExactly("RequestRateLimiter", "CircuitBreaker", "OpenSabreIpAccessControl");
        assertThat(result.route().getFilters().get(2).getArgs())
                .containsEntry("mode", "DENYLIST").containsEntry("cidrs", "10.0.0.0/8");
        assertThat(result.route().getMetadata())
                .containsEntry("connect-timeout", 500)
                .containsEntry("response-timeout", 2000);
        assertThat(result.circuitBreakerInstances()).containsKey("route-api-users-get");
    }

    @Test
    void disabledPolicyDoesNotGenerateRuntimeConfiguration() {
        GatewayRoute route = route();
        compiler.apply(route, List.of(new EffectivePolicy(PolicyType.TIMEOUT, PolicyMode.DISABLED,
                null, PolicyScopeType.API, "api-1", 1)));
        assertThat(route.getMetadata()).isEmpty();
        assertThat(route.getFilters()).isEmpty();
    }

    @Test
    void rejectsDuplicateEffectivePolicies() {
        EffectivePolicy timeout = effective(PolicyType.TIMEOUT, new TimeoutPolicyConfig(500, 2000));
        assertThatThrownBy(() -> compiler.apply(route(), List.of(timeout, timeout)))
                .hasMessageContaining("重复策略");
    }

    @Test
    void rejectsUnsupportedIdentityResolver() {
        EffectivePolicy rateLimit = effective(PolicyType.RATE_LIMIT,
                new RateLimitPolicyConfig(RateLimitPolicyConfig.KeyType.OAUTH_CLIENT, 10, 20, 1));
        assertThatThrownBy(() -> compiler.apply(route(), List.of(rateLimit)))
                .hasMessageContaining("尚未提供可信");
    }

    private EffectivePolicy effective(PolicyType type, Object config) {
        return new EffectivePolicy(type, PolicyMode.ENABLED, config,
                PolicyScopeType.API, "api-1", 1);
    }

    private GatewayRoute route() {
        GatewayRoute route = new GatewayRoute();
        route.setId("api-users-get");
        route.setUri("lb://users");
        return route;
    }
}
