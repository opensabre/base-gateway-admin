package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Type-safe global response security headers and restricted custom header operations. */
public record SecurityHeadersPolicyConfig(
        boolean hstsEnabled,
        long hstsMaxAgeSeconds,
        boolean hstsIncludeSubDomains,
        boolean hstsPreload,
        boolean contentTypeOptions,
        @NotNull FrameOptions frameOptions,
        @NotNull ReferrerPolicy referrerPolicy,
        @Size(max = 1000) String contentSecurityPolicy,
        @NotNull @Size(max = 20) List<@NotNull @Valid Header> requestHeaders,
        @NotNull @Size(max = 20) List<@NotNull @Valid Header> responseHeaders,
        @NotNull @Size(max = 20) List<@NotBlank @Pattern(regexp = "[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}") String> removeRequestHeaders,
        @NotNull @Size(max = 20) List<@NotBlank @Pattern(regexp = "[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}") String> removeResponseHeaders) {

    public enum FrameOptions { DENY, SAMEORIGIN, DISABLED }

    public enum ReferrerPolicy {
        NO_REFERRER("no-referrer"),
        SAME_ORIGIN("same-origin"),
        STRICT_ORIGIN("strict-origin"),
        STRICT_ORIGIN_WHEN_CROSS_ORIGIN("strict-origin-when-cross-origin"),
        DISABLED("");

        private final String headerValue;
        ReferrerPolicy(String headerValue) { this.headerValue = headerValue; }
        public String headerValue() { return headerValue; }
    }

    public record Header(
            @NotBlank @Pattern(regexp = "[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}") String name,
            @NotBlank @Size(max = 500) String value) {
    }

    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "host", "connection", "content-length",
            "transfer-encoding", "upgrade", "proxy-authorization", "proxy-authenticate",
            "strict-transport-security", "x-content-type-options", "x-frame-options",
            "referrer-policy", "content-security-policy");

    @AssertTrue(message = "安全响应头包含非法 HSTS 参数、重复项或受保护 Header")
    public boolean isValidConfiguration() {
        if (hstsMaxAgeSeconds < 0 || hstsMaxAgeSeconds > 63_072_000) return false;
        if (containsLineBreak(contentSecurityPolicy)) return false;
        return validHeaders(requestHeaders) && validHeaders(responseHeaders)
                && validNames(removeRequestHeaders) && validNames(removeResponseHeaders);
    }

    private boolean validHeaders(List<Header> headers) {
        if (headers == null) return true;
        Set<String> names = new java.util.HashSet<>();
        return headers.stream().allMatch(header -> header != null && header.name() != null
                && !containsLineBreak(header.value()) && validName(header.name(), names));
    }

    private boolean validNames(List<String> headers) {
        if (headers == null) return true;
        Set<String> names = new java.util.HashSet<>();
        return headers.stream().allMatch(name -> name != null && validName(name, names));
    }

    private boolean validName(String name, Set<String> names) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return !PROTECTED_HEADERS.contains(normalized) && names.add(normalized);
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }
}
