package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Type-safe path-based CORS rules compiled to Spring Cloud Gateway globalcors. */
public record CorsPolicyConfig(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Rule> rules,
        boolean addToSimpleUrlHandlerMapping) {

    public record Rule(
            @NotBlank @Size(max = 200) @Pattern(regexp = "/.*") String pathPattern,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 200) String> allowedOrigins,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 200) String> allowedOriginPatterns,
            @NotNull @Size(min = 1, max = 9) List<@NotBlank @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD|TRACE|CONNECT") String> allowedMethods,
            @NotNull @Size(min = 1, max = 50) List<@NotBlank @Pattern(regexp = "\\*|[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}") String> allowedHeaders,
            @NotNull @Size(max = 50) List<@NotBlank @Pattern(regexp = "[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}") String> exposedHeaders,
            boolean allowCredentials,
            long maxAgeSeconds) {

        private static final Set<String> METHODS = Set.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE", "CONNECT");

        @AssertTrue(message = "跨域来源、方法或缓存时间配置不合法")
        public boolean isValidConfiguration() {
            if (maxAgeSeconds < 0 || maxAgeSeconds > 86400 || allowedOrigins == null
                    || allowedOriginPatterns == null || allowedMethods == null) return false;
            if (allowedOrigins.isEmpty() && allowedOriginPatterns.isEmpty()) return false;
            if (allowedOrigins.stream().anyMatch(java.util.Objects::isNull)
                    || allowedOriginPatterns.stream().anyMatch(java.util.Objects::isNull)
                    || allowedMethods.stream().anyMatch(java.util.Objects::isNull)) return false;
            if (allowCredentials && allowedOrigins.stream().anyMatch("*"::equals)) return false;
            if (allowedMethods.stream().anyMatch(method -> !METHODS.contains(method))) return false;
            return allowedOrigins.stream().allMatch(this::validOrigin)
                    && allowedOriginPatterns.stream().allMatch(this::validPattern);
        }

        private boolean validOrigin(String value) {
            if ("*".equals(value)) return true;
            try {
                URI uri = URI.create(value);
                return Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                        && uri.getPath().isEmpty() && uri.getQuery() == null && uri.getFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private boolean validPattern(String value) {
            if ("*".equals(value)) return true;
            if (value == null || value.isBlank() || value.contains("/") && !value.startsWith("http")) return false;
            String candidate = value.replace("*.", "wildcard.");
            return validOrigin(candidate) && value.chars().filter(character -> character == '*').count() <= 1;
        }
    }

    @AssertTrue(message = "跨域 Path Pattern 不能重复")
    public boolean isUniquePathPatterns() {
        if (rules == null) return true;
        Set<String> patterns = new HashSet<>();
        return rules.stream().allMatch(rule -> rule != null && rule.pathPattern() != null
                && patterns.add(rule.pathPattern()));
    }
}
