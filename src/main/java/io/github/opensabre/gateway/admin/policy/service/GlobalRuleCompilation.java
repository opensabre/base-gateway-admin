package io.github.opensabre.gateway.admin.policy.service;

import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;

import java.util.List;
import java.util.Map;

/** Optional global rule changes produced during release validation; false means preserve the Nacos node. */
public record GlobalRuleCompilation(
        boolean defaultFiltersChanged,
        List<GatewayRouteDefinition> defaultFilters,
        boolean corsChanged,
        Map<String, Map<String, Object>> corsConfigurations,
        boolean addToSimpleUrlHandlerMapping) {
}
