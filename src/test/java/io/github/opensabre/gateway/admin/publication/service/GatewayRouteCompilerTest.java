package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.api.model.ApiDiscoveryStatus;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.publication.model.AuthMode;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.RiskLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;

import java.util.Map;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRouteCompilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayRouteCompiler compiler = new GatewayRouteCompiler(objectMapper);

    @Test
    void compilesApiBeforeApplicationRoute() {
        var routes = compiler.compile(List.of(candidate("api-1", "GET", "/users/{id}", "/api/users/{id}")),
                List.of(application("app-1", "/api/users/**", "GET", RiskLevel.HIGH)));

        assertThat(routes).hasSize(2);
        assertThat(routes.get(0).getId()).isEqualTo("api-api-1");
        assertThat(routes.get(0).getOrder()).isLessThan(routes.get(1).getOrder());
        assertThat(routes.get(0).getPredicates()).extracting("name")
                .containsExactly("Method", "Path");
        assertThat(routes.get(0).getFilters()).extracting("name").containsExactly("SetPath");
    }

    @Test
    void rejectsDuplicateMethodAndPath() {
        assertThatThrownBy(() -> compiler.compile(List.of(
                candidate("api-1", "GET", "/users", "/api/users"),
                candidate("api-2", "GET", "/other", "/api/users")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复路由匹配");
    }

    @Test
    void rejectsMissingApi() {
        var candidate = candidate("api-1", "GET", "/users", "/api/users");
        candidate.api().setDiscoveryStatus(ApiDiscoveryStatus.MISSING);
        assertThatThrownBy(() -> compiler.compile(List.of(candidate), List.of()))
                .hasMessageContaining("MISSING API");
    }

    @Test
    void requiresResourceForResourceProtectedApi() {
        var candidate = candidate("api-1", "GET", "/users", "/api/users");
        candidate.publication().setAuthMode(AuthMode.RESOURCE_REQUIRED);
        assertThatThrownBy(() -> compiler.compile(List.of(candidate), List.of()))
                .hasMessageContaining("必须关联资源");
    }

    @Test
    void classifiesWideApplicationRoutesAsHighRisk() {
        assertThat(compiler.classifyApplicationRisk("/**", null)).isEqualTo(RiskLevel.HIGH);
        assertThat(compiler.classifyApplicationRisk("/base-organization/**", "GET"))
                .isEqualTo(RiskLevel.HIGH);
        assertThat(compiler.classifyApplicationRisk("/api/users/*", "GET"))
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void compilesConfiguredApplicationPredicatesAndFilters() throws Exception {
        GatewayApplicationRoute application = application("app-1", "/api/org/**", null, RiskLevel.HIGH);
        application.setRouteOrder(320);
        application.setPredicatesJson(objectMapper.writeValueAsString(List.of(
                definition("Host", Map.of("patterns", "admin.example.com")),
                definition("Path", Map.of("pattern", "/api/org/**")))));
        application.setFiltersJson(objectMapper.writeValueAsString(List.of(
                definition("StripPrefix", Map.of("parts", "2")))));

        var route = compiler.compile(List.of(), List.of(application)).get(0);

        assertThat(route.getOrder()).isEqualTo(320);
        assertThat(route.getPredicates()).extracting("name").containsExactly("Host", "Path");
        assertThat(route.getFilters()).extracting("name").containsExactly("StripPrefix");
    }

    @Test
    void configuredApiFiltersReplaceAutomaticPathConversion() throws Exception {
        var candidate = candidate("api-1", "GET", "/users/{id}", "/open/users/{id}");
        candidate.publication().setFiltersJson(objectMapper.writeValueAsString(List.of(
                definition("SetPath", Map.of("template", "/users/{id}")),
                definition("AddRequestHeader", Map.of("name", "X-Source", "value", "gateway")))));

        var route = compiler.compile(List.of(candidate), List.of()).get(0);

        assertThat(route.getFilters()).extracting("name")
                .containsExactly("SetPath", "AddRequestHeader");
    }

    private GatewayRouteDefinition definition(String name, Map<String, String> args) {
        GatewayRouteDefinition definition = new GatewayRouteDefinition();
        definition.setName(name);
        definition.setArgs(args);
        return definition;
    }

    private GatewayRouteCompiler.ApiPublicationCandidate candidate(
            String id, String method, String upstreamPath, String externalPath) {
        GatewayApi api = new GatewayApi();
        api.setId(id);
        api.setServiceId("base-organization");
        api.setHttpMethod(method);
        api.setUpstreamPath(upstreamPath);
        api.setDiscoveryStatus(ApiDiscoveryStatus.ACTIVE);
        GatewayApiPublication publication = new GatewayApiPublication();
        publication.setApiId(id);
        publication.setExternalPath(externalPath);
        publication.setAuthMode(AuthMode.AUTHENTICATED);
        publication.setRiskLevel(RiskLevel.LOW);
        return new GatewayRouteCompiler.ApiPublicationCandidate(api, publication);
    }

    private GatewayApplicationRoute application(String id, String path, String method, RiskLevel risk) {
        GatewayApplicationRoute route = new GatewayApplicationRoute();
        route.setId(id);
        route.setServiceId("base-organization");
        route.setRouteName("organization wildcard");
        route.setExternalPath(path);
        route.setTargetUri("lb://base-organization");
        route.setHttpMethod(method);
        route.setRiskLevel(risk);
        return route;
    }
}
