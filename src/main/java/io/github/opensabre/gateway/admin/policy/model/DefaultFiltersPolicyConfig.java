package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Editable draft representation of spring.cloud.gateway.server.webflux.default-filters. */
public record DefaultFiltersPolicyConfig(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Filter> filters) {

    public record Filter(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9]{0,99}") String name,
            @NotNull @Size(max = 30) Map<
                    @NotBlank @Size(max = 200) String,
                    @NotNull @Size(max = 2000) String> args,
            boolean enabled) {
    }

    @AssertTrue(message = "全局过滤器必须包含且启用唯一的 TokenRelay，参数不能包含换行")
    public boolean isValidConfiguration() {
        return filters != null && filters.stream()
                .filter(filter -> filter != null && "TokenRelay".equals(filter.name()) && filter.enabled())
                .count() == 1 && filters.stream().filter(java.util.Objects::nonNull)
                .flatMap(filter -> filter.args() == null ? java.util.stream.Stream.empty()
                        : filter.args().entrySet().stream())
                .noneMatch(entry -> containsLineBreak(entry.getKey()) || containsLineBreak(entry.getValue()));
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }
}
