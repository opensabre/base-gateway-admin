package io.github.opensabre.gateway.admin.policy.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFiltersPolicyConfigTest {

    @Test
    void requiresOneEnabledTokenRelay() {
        assertThat(config(List.of(filter("Retry", Map.of("retries", "3"), true)))
                .isValidConfiguration()).isFalse();
        assertThat(config(List.of(filter("TokenRelay", Map.of(), false)))
                .isValidConfiguration()).isFalse();
        assertThat(config(List.of(filter("TokenRelay", Map.of(), true)))
                .isValidConfiguration()).isTrue();
    }

    @Test
    void rejectsLineBreakInArguments() {
        assertThat(config(List.of(filter("TokenRelay", Map.of(), true),
                filter("AddResponseHeader", Map.of("value", "safe\r\ninjected"), true)))
                .isValidConfiguration()).isFalse();
    }

    private DefaultFiltersPolicyConfig config(List<DefaultFiltersPolicyConfig.Filter> filters) {
        return new DefaultFiltersPolicyConfig(filters);
    }

    private DefaultFiltersPolicyConfig.Filter filter(String name, Map<String, String> args, boolean enabled) {
        return new DefaultFiltersPolicyConfig.Filter(name, args, enabled);
    }
}
