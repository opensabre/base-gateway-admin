package io.github.opensabre.gateway.admin.route.service.impl;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import io.github.opensabre.gateway.admin.route.model.GatewayOauth2Client;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class GatewayRouteConfigServiceTest {

    @Test
    void shouldAcceptCompiledIpAccessControlFilter() {
        GatewayRoute route = route("managed-api", "lb://service", "Path", "pattern", "/api/**");
        route.setFilters(List.of(definition("OpenSabreIpAccessControl",
                Map.of("mode", "DENYLIST", "cidrs", "10.0.0.0/8"))));

        GatewayRouteConfigService.validateRoute(route);
    }

    @Test
    void shouldUseStarterNacosClientForCasPublication() throws Exception {
        String yaml = "spring:\n  cloud:\n    gateway:\n      routes: []\n";
        NacosConfigManager manager = mock(NacosConfigManager.class);
        ConfigService configService = mock(ConfigService.class);
        when(manager.getConfigService()).thenReturn(configService);
        when(configService.getConfig("base-gateway.yml", "DEFAULT_GROUP", 10_000L)).thenReturn(yaml);
        when(configService.publishConfigCas(eq("base-gateway.yml"), eq("DEFAULT_GROUP"),
                anyString(), anyString(), eq("yaml"))).thenReturn(true);
        GatewayRouteConfigService service = new GatewayRouteConfigService(
                manager, "base-gateway.yml", "DEFAULT_GROUP");
        String baseVersion = service.getCurrentConfig().getVersion();

        service.publishManaged(baseVersion, "release-1", List.of(), Map.of(),
                new io.github.opensabre.gateway.admin.policy.service.GlobalRuleCompilation(
                        false, List.of(), false, Map.of(), false));

        verify(configService).publishConfigCas(eq("base-gateway.yml"), eq("DEFAULT_GROUP"),
                anyString(), eq(baseVersion), eq("yaml"));
    }

    @Test
    void shouldParseShortFormGatewayDefinitions() {
        String yaml = """
                spring:
                  cloud:
                    gateway:
                      routes:
                        - id: base-organization
                          uri: lb://base-organization
                          predicates:
                            - Path=/api/org/**
                          filters:
                            - StripPrefix=2
                """;

        List<GatewayRoute> routes = GatewayRouteConfigService.parseRoutes(yaml);

        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route.getId()).isEqualTo("base-organization");
            assertThat(route.getPredicates()).singleElement().satisfies(predicate ->
                    assertThat(predicate.getArgs()).containsEntry("pattern", "/api/org/**"));
            assertThat(route.getFilters()).singleElement().satisfies(filter ->
                    assertThat(filter.getArgs()).containsEntry("value", "2"));
        });
    }

    @Test
    void shouldReplaceOnlyGatewayRouteNode() {
        String yaml = """
                management:
                  endpoints:
                    web:
                      exposure:
                        include: health
                spring:
                  cloud:
                    gateway:
                      discovery:
                        locator:
                          enabled: true
                      routes: []
                """;
        GatewayRoute route = route("base-organization", "lb://base-organization", "Path", "pattern", "/api/org/**");
        route.setMetadata(Map.of("connect-timeout", 500, "response-timeout", 2000));

        String updated = GatewayRouteConfigService.replaceRoutes(yaml, List.of(route));

        assertThat(updated).contains("management:", "discovery:", "enabled: true");
        assertThat(GatewayRouteConfigService.parseRoutes(updated)).singleElement()
                .satisfies(parsed -> {
                    assertThat(parsed.getId()).isEqualTo("base-organization");
                    assertThat(parsed.getMetadata()).containsEntry("connect-timeout", 500)
                            .containsEntry("response-timeout", 2000);
                });
    }

    @Test
    void shouldRejectUnsupportedFilter() {
        GatewayRoute route = route("base-org", "lb://base-organization", "Path", "pattern", "/api/org/**");
        GatewayRouteDefinition filter = new GatewayRouteDefinition();
        filter.setName("SetStatus");
        filter.setArgs(Map.of("status", "200"));
        route.setFilters(List.of(filter));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> GatewayRouteConfigService.validateRoute(route))
                .withMessageContaining("不支持的过滤器");
    }

    @Test
    void shouldReplaceOnlyGatewayDefaultFiltersNode() {
        String yaml = """
                spring:
                  cloud:
                    gateway:
                      routes:
                        - id: base-organization
                          uri: lb://base-organization
                      default-filters:
                        - AddResponseHeader=X-Old, old
                """;
        GatewayRouteDefinition filter = definition("AddResponseHeader", Map.of("name", "X-Trace", "value", "enabled"));

        String updated = GatewayRouteConfigService.replaceDefaultFilters(yaml, List.of(filter));

        assertThat(GatewayRouteConfigService.parseRoutes(updated)).singleElement()
                .extracting(GatewayRoute::getId).isEqualTo("base-organization");
        assertThat(GatewayRouteConfigService.parseDefaultFilters(updated)).singleElement()
                .satisfies(item -> assertThat(item.getArgs()).containsEntry("name", "X-Trace"));
    }

    @Test
    void shouldRejectInvalidDefaultRateLimit() {
        GatewayRouteDefinition tokenRelay = definition("TokenRelay", Map.of());
        GatewayRouteDefinition rateLimit = definition("RequestRateLimiter", Map.of(
                "redis-rate-limiter.replenishRate", "10",
                "redis-rate-limiter.burstCapacity", "5",
                "rate-limiter", "#{@defaultRedisRateLimiter}",
                "key-resolver", "#{@remoteAddressKeyResolver}"));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> GatewayRouteConfigService.validateDefaultFilters(List.of(tokenRelay, rateLimit)))
                .withMessageContaining("突发容量不能小于补充速率");
    }

    @Test
    void shouldRejectRemovingTokenRelayFromDefaultFilters() {
        GatewayRouteDefinition responseHeader = definition(
                "AddResponseHeader", Map.of("name", "X-Trace", "value", "enabled"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GatewayRouteConfigService.validateDefaultFilters(List.of(responseHeader)))
                .withMessageContaining("必须保留 TokenRelay");
    }

    @Test
    void shouldReplaceAndDisableGlobalCorsWithoutChangingRoutes() {
        String content = "spring:\n  cloud:\n    gateway:\n      routes:\n        - id: keep\n          uri: lb://keep\n";
        String enabled = GatewayRouteConfigService.replaceGlobalCors(content,
                Map.of("/**", Map.of("allowedOrigins", List.of("https://admin.example.com"),
                        "allowedMethods", List.of("GET", "OPTIONS"))), true);

        assertThat(enabled).contains("globalcors:").contains("https://admin.example.com")
                .contains("id: keep");
        assertThat(GatewayRouteConfigService.parseGlobalCorsConfigurations(enabled))
                .containsKey("/**");
        assertThat(GatewayRouteConfigService.parseGlobalCorsSimpleHandler(enabled)).isTrue();
        assertThat(GatewayRouteConfigService.replaceGlobalCors(enabled, Map.of(), false))
                .doesNotContain("globalcors:").contains("id: keep");
    }

    @Test
    void shouldReplaceOauth2RegistrationsWithoutChangingRoutes() {
        String yaml = """
                spring:
                  cloud:
                    gateway:
                      routes: []
                  security:
                    oauth2:
                      client:
                        provider:
                          custom-issuer:
                            issuer-uri: http://authorization:8000
                        registration: {}
                """;
        GatewayOauth2Client client = new GatewayOauth2Client();
        client.setRegistrationId("base-gateway-local");
        client.setProvider("custom-issuer");
        client.setIssuerUri("http://authorization:8000");
        client.setClientId("base-gateway-local");
        client.setClientSecret("ENC(ciphertext)");
        client.setRedirectUri("http://localhost:3000/login/oauth2/code/base-gateway-local");
        client.setScopes(List.of("openid", "profile"));

        String updated = GatewayRouteConfigService.replaceOauth2Clients(yaml, List.of(client));

        assertThat(GatewayRouteConfigService.parseRoutes(updated)).isEmpty();
        assertThat(GatewayRouteConfigService.parseOauth2Clients(updated)).singleElement()
                .satisfies(item -> {
                    assertThat(item.getRegistrationId()).isEqualTo("base-gateway-local");
                    assertThat(item.getIssuerUri()).isEqualTo("http://authorization:8000");
                    assertThat(item.getRedirectUri())
                            .isEqualTo("http://localhost:3000/login/oauth2/code/base-gateway-local");
                    assertThat(item.getClientSecret()).isEqualTo("ENC(ciphertext)");
                });
    }

    @Test
    void shouldAllowConfiguredOpensabreHttpCallbackOnly() {
        GatewayOauth2Client client = oauth2Client("http://opensabre:8080/login/oauth2/code/base-gateway-client");

        GatewayRouteConfigService.validateOauth2Clients(List.of(client));

        client.setRedirectUri("http://example.com/login/oauth2/code/base-gateway-client");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GatewayRouteConfigService.validateOauth2Clients(List.of(client)))
                .withMessageContaining("回调地址");
    }

    @Test
    void shouldReplaceOnlyManagedCircuitBreakerInstances() {
        String yaml = """
                spring:
                  cloud:
                    gateway:
                      routes: []
                resilience4j:
                  circuitbreaker:
                    instances:
                      manually-maintained:
                        failureRateThreshold: 20
                      route-api-old:
                        failureRateThreshold: 10
                """;

        String updated = GatewayRouteConfigService.replaceManagedCircuitBreakers(yaml, Map.of(
                "route-api-new", Map.of("failureRateThreshold", 50)));

        assertThat(updated).contains("manually-maintained:", "route-api-new:", "failureRateThreshold: 50")
                .doesNotContain("route-api-old:");
    }

    @Test
    void shouldWriteReleaseRevisionWithoutRemovingOtherGatewayProperties() {
        String yaml = """
                spring:
                  cloud:
                    gateway:
                      routes: []
                opensabre:
                  gateway:
                    permission:
                      enabled: true
                """;

        String updated = GatewayRouteConfigService.replaceGatewayRevision(yaml, "release-42");

        assertThat(updated).contains("permission:", "enabled: true", "revision: release-42");
    }

    @Test
    void shouldCompileApiAccessRulesFromManagedRouteMetadata() {
        String yaml = "spring:\n  cloud:\n    gateway:\n      routes: []\n";
        GatewayRoute route = route("api-100", "lb://orders", "Method", "method", "GET");
        route.setPredicates(List.of(
                definition("Method", Map.of("method", "GET")),
                definition("Path", Map.of("pattern", "/orders/{id}"))));
        route.setMetadata(Map.of("opensabre-auth-mode", "RESOURCE_REQUIRED"));

        String updated = GatewayRouteConfigService.replaceApiAccessRules(yaml, List.of(route));

        assertThat(updated).contains("route-id: api-100", "method: GET",
                "path: '/orders/{id}'", "mode: RESOURCE_REQUIRED");
    }

    private GatewayOauth2Client oauth2Client(String redirectUri) {
        GatewayOauth2Client client = new GatewayOauth2Client();
        client.setRegistrationId("base-gateway-client");
        client.setProvider("custom-issuer");
        client.setIssuerUri("http://base-authorization:8000");
        client.setClientId("base-gateway");
        client.setClientSecret("secret");
        client.setRedirectUri(redirectUri);
        client.setScopes(List.of("openid"));
        return client;
    }

    private GatewayRouteDefinition definition(String name, Map<String, String> args) {
        GatewayRouteDefinition definition = new GatewayRouteDefinition();
        definition.setName(name);
        definition.setArgs(args);
        return definition;
    }

    private GatewayRoute route(String id, String uri, String predicateName, String argName, String argValue) {
        GatewayRoute route = new GatewayRoute();
        route.setId(id);
        route.setUri(uri);
        GatewayRouteDefinition predicate = new GatewayRouteDefinition();
        predicate.setName(predicateName);
        predicate.setArgs(Map.of(argName, argValue));
        route.setPredicates(List.of(predicate));
        return route;
    }
}
