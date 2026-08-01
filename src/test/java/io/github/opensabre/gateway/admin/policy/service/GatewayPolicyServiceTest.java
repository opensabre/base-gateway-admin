package io.github.opensabre.gateway.admin.policy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.policy.dao.GatewayPolicyMapper;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.GatewayPolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyChange;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.PolicyScopeType;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.model.TimeoutPolicyConfig;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayPolicyServiceTest {

    private final GatewayPolicyMapper mapper = mock(GatewayPolicyMapper.class);
    private final List<GatewayPolicy> policies = new ArrayList<>();
    private GatewayPolicyService service;

    @BeforeEach
    void setUp() {
        service = new GatewayPolicyService(mapper, new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        when(mapper.selectOne(ArgumentMatchers.any())).thenAnswer(invocation -> {
            var wrapper = invocation.getArgument(0);
            // The resolver query order is deterministic; tests provide records through a small mapper answer queue.
            return policies.isEmpty() ? null : policies.remove(0);
        });
    }

    @Test
    void apiPolicyWinsOverApplicationAndGlobal() {
        policies.add(policy(PolicyScopeType.API, "api-1", PolicyMode.ENABLED, 50));
        EffectivePolicy result = service.resolve(PolicyType.TIMEOUT, "service-1", "api-1");
        assertThat(result.sourceScope()).isEqualTo(PolicyScopeType.API);
        assertThat(((TimeoutPolicyConfig) result.effectiveConfig()).responseTimeoutMs()).isEqualTo(50);
    }

    @Test
    void inheritFallsThroughToApplication() {
        policies.add(policy(PolicyScopeType.API, "api-1", PolicyMode.INHERIT, null));
        policies.add(policy(PolicyScopeType.APPLICATION, "service-1", PolicyMode.ENABLED, 100));
        EffectivePolicy result = service.resolve(PolicyType.TIMEOUT, "service-1", "api-1");
        assertThat(result.sourceScope()).isEqualTo(PolicyScopeType.APPLICATION);
        assertThat(((TimeoutPolicyConfig) result.effectiveConfig()).responseTimeoutMs()).isEqualTo(100);
    }

    @Test
    void apiDisabledStopsInheritance() {
        policies.add(policy(PolicyScopeType.API, "api-1", PolicyMode.DISABLED, null));
        EffectivePolicy result = service.resolve(PolicyType.TIMEOUT, "service-1", "api-1");
        assertThat(result.effectiveMode()).isEqualTo(PolicyMode.DISABLED);
        assertThat(result.sourceScope()).isEqualTo(PolicyScopeType.API);
        assertThat(result.effectiveConfig()).isNull();
    }

    @Test
    void enabledPolicyRequiresMatchingTypedConfig() {
        PolicyChange change = new PolicyChange(PolicyMode.ENABLED, null, null, null, null);
        assertThatThrownBy(() -> service.save(PolicyScopeType.GLOBAL, null, PolicyType.TIMEOUT, change))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须提供");
    }

    @Test
    void disabledPolicyRejectsParameters() {
        PolicyChange change = new PolicyChange(PolicyMode.DISABLED, null,
                new TimeoutPolicyConfig(100, 200), null, null);
        assertThatThrownBy(() -> service.save(PolicyScopeType.GLOBAL, null, PolicyType.TIMEOUT, change))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能携带");
    }

    private GatewayPolicy policy(PolicyScopeType scope, String scopeId, PolicyMode mode, Integer timeout) {
        GatewayPolicy policy = new GatewayPolicy();
        policy.setScopeType(scope);
        policy.setScopeId(scopeId);
        policy.setPolicyType(PolicyType.TIMEOUT);
        policy.setMode(mode);
        policy.setLockVersion(3);
        if (timeout != null) {
            policy.setConfigJson("{\"connectTimeoutMs\":10,\"responseTimeoutMs\":" + timeout + "}");
        }
        return policy;
    }
}
