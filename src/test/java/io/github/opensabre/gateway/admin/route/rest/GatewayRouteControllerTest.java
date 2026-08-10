package io.github.opensabre.gateway.admin.route.rest;

import io.github.opensabre.gateway.admin.integration.GatewayIntegrationProperties;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2ClientChange;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GatewayRouteControllerTest {

    @Test
    void shouldRejectWritesWhenEmergencyWriteFreezeIsEnabled() {
        IGatewayRouteConfigService service = mock(IGatewayRouteConfigService.class);
        GatewayIntegrationProperties properties = new GatewayIntegrationProperties();
        properties.setConfigurationWriteEnabled(false);
        GatewayRouteController controller = new GatewayRouteController(service, properties);

        assertThatIllegalStateException()
                .isThrownBy(() -> controller.updateOauth2Clients(new GatewayOauth2ClientChange()))
                .withMessageContaining("管理员停用");
        verifyNoInteractions(service);
    }
}
