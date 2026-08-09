package io.github.opensabre.gateway.admin.policy.service;

import io.github.opensabre.gateway.admin.policy.model.CorsPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.DefaultFiltersPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.SecurityHeadersPolicyConfig;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles typed global policies into the only two globally managed Gateway nodes. */
@Component
public class GatewayGlobalRuleCompiler {

    public GlobalRuleCompilation compile(EffectivePolicy defaultFilters, EffectivePolicy securityHeaders,
            EffectivePolicy cors) {
        boolean filtersChanged = defaultFilters != null && defaultFilters.sourceScope() != null;
        boolean headersChanged = !filtersChanged && securityHeaders != null && securityHeaders.sourceScope() != null;
        boolean corsChanged = cors != null && cors.sourceScope() != null;
        List<GatewayRouteDefinition> filters = filtersChanged
                ? compileDefaultFilters(defaultFilters)
                : headersChanged ? compileHeaders(securityHeaders) : List.of();
        CorsCompilation compiledCors = corsChanged ? compileCors(cors) : new CorsCompilation(Map.of(), false);
        return new GlobalRuleCompilation(filtersChanged || headersChanged, filters, corsChanged,
                compiledCors.configurations(), compiledCors.addToSimpleUrlHandlerMapping());
    }

    private List<GatewayRouteDefinition> compileDefaultFilters(EffectivePolicy policy) {
        if (policy.effectiveMode() != PolicyMode.ENABLED
                || !(policy.effectiveConfig() instanceof DefaultFiltersPolicyConfig config)) {
            throw new IllegalArgumentException("DEFAULT_FILTERS 必须启用并提供配置");
        }
        return config.filters().stream().filter(DefaultFiltersPolicyConfig.Filter::enabled)
                .map(filter -> definition(filter.name(), filter.args()))
                .toList();
    }

    private List<GatewayRouteDefinition> compileHeaders(EffectivePolicy policy) {
        List<GatewayRouteDefinition> filters = new ArrayList<>();
        filters.add(definition("TokenRelay", Map.of()));
        if (policy.effectiveMode() != PolicyMode.ENABLED) return List.copyOf(filters);
        if (!(policy.effectiveConfig() instanceof SecurityHeadersPolicyConfig config)) {
            throw new IllegalArgumentException("SECURITY_HEADERS Effective Policy 配置类型不正确");
        }
        if (config.hstsEnabled()) {
            String value = "max-age=" + config.hstsMaxAgeSeconds()
                    + (config.hstsIncludeSubDomains() ? "; includeSubDomains" : "")
                    + (config.hstsPreload() ? "; preload" : "");
            replaceResponseHeader(filters, "Strict-Transport-Security", value);
        }
        if (config.contentTypeOptions()) replaceResponseHeader(filters, "X-Content-Type-Options", "nosniff");
        if (config.frameOptions() != SecurityHeadersPolicyConfig.FrameOptions.DISABLED) {
            replaceResponseHeader(filters, "X-Frame-Options", config.frameOptions().name());
        }
        if (config.referrerPolicy() != SecurityHeadersPolicyConfig.ReferrerPolicy.DISABLED) {
            replaceResponseHeader(filters, "Referrer-Policy", config.referrerPolicy().headerValue());
        }
        if (config.contentSecurityPolicy() != null && !config.contentSecurityPolicy().isBlank()) {
            replaceResponseHeader(filters, "Content-Security-Policy", config.contentSecurityPolicy().trim());
        }
        config.removeRequestHeaders().forEach(name -> filters.add(definition("RemoveRequestHeader", Map.of("name", name))));
        config.removeResponseHeaders().forEach(name -> filters.add(definition("RemoveResponseHeader", Map.of("name", name))));
        config.requestHeaders().forEach(header -> filters.add(definition("AddRequestHeader",
                Map.of("name", header.name(), "value", header.value()))));
        config.responseHeaders().forEach(header -> replaceResponseHeader(filters, header.name(), header.value()));
        return List.copyOf(filters);
    }

    private CorsCompilation compileCors(EffectivePolicy policy) {
        if (policy.effectiveMode() != PolicyMode.ENABLED) return new CorsCompilation(Map.of(), false);
        if (!(policy.effectiveConfig() instanceof CorsPolicyConfig config)) {
            throw new IllegalArgumentException("CORS Effective Policy 配置类型不正确");
        }
        Map<String, Map<String, Object>> configurations = new LinkedHashMap<>();
        for (CorsPolicyConfig.Rule rule : config.rules()) {
            Map<String, Object> values = new LinkedHashMap<>();
            if (!rule.allowedOrigins().isEmpty()) values.put("allowedOrigins", List.copyOf(rule.allowedOrigins()));
            if (!rule.allowedOriginPatterns().isEmpty()) {
                values.put("allowedOriginPatterns", List.copyOf(rule.allowedOriginPatterns()));
            }
            values.put("allowedMethods", List.copyOf(rule.allowedMethods()));
            values.put("allowedHeaders", List.copyOf(rule.allowedHeaders()));
            if (!rule.exposedHeaders().isEmpty()) values.put("exposedHeaders", List.copyOf(rule.exposedHeaders()));
            values.put("allowCredentials", rule.allowCredentials());
            values.put("maxAge", rule.maxAgeSeconds());
            configurations.put(rule.pathPattern(), Map.copyOf(values));
        }
        return new CorsCompilation(Map.copyOf(configurations), config.addToSimpleUrlHandlerMapping());
    }

    private void replaceResponseHeader(List<GatewayRouteDefinition> filters, String name, String value) {
        filters.add(definition("RemoveResponseHeader", Map.of("name", name)));
        filters.add(definition("AddResponseHeader", Map.of("name", name, "value", value)));
    }

    private GatewayRouteDefinition definition(String name, Map<String, String> args) {
        GatewayRouteDefinition definition = new GatewayRouteDefinition();
        definition.setName(name);
        definition.setArgs(args);
        return definition;
    }

    private record CorsCompilation(Map<String, Map<String, Object>> configurations,
                                   boolean addToSimpleUrlHandlerMapping) {
    }
}
