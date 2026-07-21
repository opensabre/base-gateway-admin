package io.github.opensabre.gateway.admin.route.rest;

import io.github.opensabre.gateway.admin.integration.GatewayIntegrationProperties;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteChange;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GatewayRouteControllerTest {

    @Test
    void shouldRejectWritesBeforeControlPlaneCutover() {
        IGatewayRouteConfigService service = mock(IGatewayRouteConfigService.class);
        GatewayIntegrationProperties properties = new GatewayIntegrationProperties();
        GatewayRouteController controller = new GatewayRouteController(service, properties);

        assertThatIllegalStateException()
                .isThrownBy(() -> controller.create(new GatewayRouteChange()))
                .withMessageContaining("尚未切换");
        verifyNoInteractions(service);
    }
}
