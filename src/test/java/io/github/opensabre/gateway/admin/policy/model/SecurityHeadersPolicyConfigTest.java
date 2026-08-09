package io.github.opensabre.gateway.admin.policy.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersPolicyConfigTest {

    @Test
    void rejectsProtectedHeaderAndLineBreakInjection() {
        assertThat(config(List.of(new SecurityHeadersPolicyConfig.Header("Authorization", "value")))
                .isValidConfiguration()).isFalse();
        assertThat(config(List.of(new SecurityHeadersPolicyConfig.Header("X-Tenant", "ok\r\ninjected")))
                .isValidConfiguration()).isFalse();
    }

    @Test
    void acceptsRestrictedCustomHeader() {
        assertThat(config(List.of(new SecurityHeadersPolicyConfig.Header("X-Tenant", "opensabre")))
                .isValidConfiguration()).isTrue();
    }

    private SecurityHeadersPolicyConfig config(List<SecurityHeadersPolicyConfig.Header> headers) {
        return new SecurityHeadersPolicyConfig(true, 31536000, true, false, true,
                SecurityHeadersPolicyConfig.FrameOptions.DENY,
                SecurityHeadersPolicyConfig.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
                "default-src 'self'", List.of(), headers, List.of(), List.of());
    }
}
