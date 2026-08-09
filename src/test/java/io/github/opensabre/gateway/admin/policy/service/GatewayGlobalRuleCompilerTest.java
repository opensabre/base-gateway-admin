package io.github.opensabre.gateway.admin.policy.service;

import io.github.opensabre.gateway.admin.policy.model.CorsPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.DefaultFiltersPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.PolicyScopeType;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.model.SecurityHeadersPolicyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayGlobalRuleCompilerTest {

    private final GatewayGlobalRuleCompiler compiler = new GatewayGlobalRuleCompiler();

    @Test
    void compilesSecurityHeadersAndCors() {
        var headers = new SecurityHeadersPolicyConfig(true, 31536000, true, true, true,
                SecurityHeadersPolicyConfig.FrameOptions.DENY,
                SecurityHeadersPolicyConfig.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
                "default-src 'self'", List.of(), List.of(), List.of(), List.of());
        var cors = new CorsPolicyConfig(List.of(new CorsPolicyConfig.Rule("/**",
                List.of("https://admin.example.com"), List.of(),
                List.of("GET", "POST", "OPTIONS"), List.of("Authorization", "Content-Type"),
                List.of("X-Request-Id"), true, 3600)), true);

        GlobalRuleCompilation result = compiler.compile(absent(PolicyType.DEFAULT_FILTERS),
                effective(PolicyType.SECURITY_HEADERS, headers),
                effective(PolicyType.CORS, cors));

        assertThat(result.defaultFiltersChanged()).isTrue();
        assertThat(result.defaultFilters()).extracting("name")
                .contains("TokenRelay", "RemoveResponseHeader", "AddResponseHeader")
                .doesNotContain("RequestRateLimiter", "Retry", "CircuitBreaker");
        assertThat(result.corsChanged()).isTrue();
        assertThat(result.corsConfigurations()).containsKey("/**");
        assertThat(result.corsConfigurations().get("/**")).containsEntry("allowCredentials", true)
                .containsEntry("maxAge", 3600L);
    }

    @Test
    void preservesNodesWhenNoGlobalPolicyExists() {
        EffectivePolicy absentHeaders = new EffectivePolicy(PolicyType.SECURITY_HEADERS,
                PolicyMode.DISABLED, null, null, null, null);
        EffectivePolicy absentCors = new EffectivePolicy(PolicyType.CORS,
                PolicyMode.DISABLED, null, null, null, null);

        GlobalRuleCompilation result = compiler.compile(absent(PolicyType.DEFAULT_FILTERS),
                absentHeaders, absentCors);

        assertThat(result.defaultFiltersChanged()).isFalse();
        assertThat(result.corsChanged()).isFalse();
    }

    @Test
    void compilesEnabledDefaultFiltersWithoutDroppingOrderOrArguments() {
        var config = new DefaultFiltersPolicyConfig(List.of(
                new DefaultFiltersPolicyConfig.Filter("TokenRelay", java.util.Map.of(), true),
                new DefaultFiltersPolicyConfig.Filter("Retry", java.util.Map.of("retries", "3"), false),
                new DefaultFiltersPolicyConfig.Filter("AddResponseHeader",
                        java.util.Map.of("name", "X-Frame-Options", "value", "DENY"), true)));

        GlobalRuleCompilation result = compiler.compile(effective(PolicyType.DEFAULT_FILTERS, config),
                absent(PolicyType.SECURITY_HEADERS), absent(PolicyType.CORS));

        assertThat(result.defaultFilters()).extracting("name")
                .containsExactly("TokenRelay", "AddResponseHeader");
        assertThat(result.defaultFilters().get(1).getArgs()).containsEntry("value", "DENY");
    }

    private EffectivePolicy effective(PolicyType type, Object config) {
        return new EffectivePolicy(type, PolicyMode.ENABLED, config,
                PolicyScopeType.GLOBAL, "GLOBAL", 0);
    }

    private EffectivePolicy absent(PolicyType type) {
        return new EffectivePolicy(type, PolicyMode.DISABLED, null, null, null, null);
    }
}
