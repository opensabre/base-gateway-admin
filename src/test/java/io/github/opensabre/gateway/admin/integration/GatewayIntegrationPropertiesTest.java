package io.github.opensabre.gateway.admin.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayIntegrationPropertiesTest {

    @Test
    void shouldEnableConfigurationWritesAfterControlPlaneMigration() {
        GatewayIntegrationProperties properties = new GatewayIntegrationProperties();

        assertThat(properties.isConfigurationWriteEnabled()).isTrue();
    }
}
