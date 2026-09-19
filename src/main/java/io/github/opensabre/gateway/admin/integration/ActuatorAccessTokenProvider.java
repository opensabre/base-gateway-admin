package io.github.opensabre.gateway.admin.integration;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/** Obtains and caches the control plane's scoped client-credentials token. */
@Component
public class ActuatorAccessTokenProvider {

    private final OAuth2AuthorizedClientManager manager;

    public ActuatorAccessTokenProvider(ClientRegistrationRepository registrations) {
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        var clientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, service);
        clientManager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        this.manager = clientManager;
    }

    public String token() {
        OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId("actuator-read")
                .principal("base-gateway-admin")
                .build());
        if (client == null) {
            throw new IllegalStateException("Actuator OAuth2 client credentials are unavailable");
        }
        return client.getAccessToken().getTokenValue();
    }
}
