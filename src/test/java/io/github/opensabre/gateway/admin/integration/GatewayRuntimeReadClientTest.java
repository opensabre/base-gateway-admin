package io.github.opensabre.gateway.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRuntimeReadClientTest {

    @Test
    void buildsRuntimeUriFromManagementMetadata() {
        var instance = new GatewayServiceInstance("10.0.0.8", 8443, "DEFAULT", true, true, 1.0,
                Map.of("management.scheme", "https", "management.host", "base-gateway",
                        "management.port", "18080", "management.path", "/manage"));

        var uri = new GatewayRuntimeReadClient(null, new ObjectMapper(), null).runtimeUri(instance);

        assertThat(uri.toString()).isEqualTo("https://base-gateway:18080/manage/gatewayruntime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsScopedBearerTokenToGatewayRuntimeEndpoint() throws Exception {
        var instance = new GatewayServiceInstance("10.0.0.8", 8443, "DEFAULT", true, true, 1.0,
                Map.of("management.host", "base-gateway", "management.port", "18080"));
        HttpClient httpClient = mock(HttpClient.class);
        ActuatorAccessTokenProvider tokenProvider = mock(ActuatorAccessTokenProvider.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(tokenProvider.token()).thenReturn("scoped-token");
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    assertThat(request.headers().firstValue("Authorization"))
                            .hasValue("Bearer scoped-token");
                    return response;
                });

        assertThatThrownBy(() -> new GatewayRuntimeReadClient(httpClient,
                new ObjectMapper(), tokenProvider).fetch(instance))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 503");
    }
}
