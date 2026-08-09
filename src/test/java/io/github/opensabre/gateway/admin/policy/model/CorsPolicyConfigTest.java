package io.github.opensabre.gateway.admin.policy.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPolicyConfigTest {

    @Test
    void rejectsWildcardOriginWithCredentials() {
        var config = config(List.of("*"), true);
        assertThat(config.isValidConfiguration()).isFalse();
    }

    @Test
    void acceptsExactCredentialedOrigin() {
        var config = config(List.of("https://admin.example.com"), true);
        assertThat(config.isValidConfiguration()).isTrue();
    }

    private CorsPolicyConfig.Rule config(List<String> origins, boolean credentials) {
        return new CorsPolicyConfig.Rule("/**", origins, List.of(),
                List.of("GET", "OPTIONS"), List.of("Authorization"), List.of(), credentials, 3600);
    }
}
