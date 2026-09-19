package io.github.opensabre.gateway.admin.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorAccessTokenProviderTest {

    @Test
    void obtainsAndCachesScopedClientCredentialsToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/oauth2/token", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"access_token\":\"scoped-token\",\"token_type\":\"Bearer\","
                    .concat("\"expires_in\":300,\"scope\":\"actuator.read\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ClientRegistration registration = ClientRegistration.withRegistrationId("actuator-read")
                    .clientId("control-plane")
                    .clientSecret("secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/token")
                    .scope("actuator.read")
                    .build();
            var provider = new ActuatorAccessTokenProvider(
                    new InMemoryClientRegistrationRepository(registration));

            assertThat(provider.token()).isEqualTo("scoped-token");
            assertThat(provider.token()).isEqualTo("scoped-token");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(authorization.get()).startsWith("Basic ");
        } finally {
            server.stop(0);
        }
    }
}
