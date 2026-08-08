package io.github.opensabre.gateway.admin.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.policy.dao.GatewayPolicyMapper;
import io.github.opensabre.gateway.admin.policy.model.AccessControlPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.CircuitBreakerPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.GatewayPolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyChange;
import io.github.opensabre.gateway.admin.policy.model.PolicyMode;
import io.github.opensabre.gateway.admin.policy.model.PolicyScopeType;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.model.RateLimitPolicyConfig;
import io.github.opensabre.gateway.admin.policy.model.TimeoutPolicyConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 保存类型化治理策略，并按 API、应用、全局顺序解析最终策略。
 */
@Service
public class GatewayPolicyService {

    public static final String GLOBAL_SCOPE_ID = "GLOBAL";

    private final GatewayPolicyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public GatewayPolicyService(GatewayPolicyMapper mapper, ObjectMapper objectMapper, Validator validator) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** 查询一个作用域下的全部策略。 */
    public List<GatewayPolicy> list(PolicyScopeType scopeType, String scopeId) {
        String normalizedScopeId = normalizeScopeId(scopeType, scopeId);
        return mapper.selectList(new LambdaQueryWrapper<GatewayPolicy>()
                .eq(GatewayPolicy::getScopeType, scopeType)
                .eq(GatewayPolicy::getScopeId, normalizedScopeId)
                .orderByAsc(GatewayPolicy::getPolicyType));
    }

    /**
     * 新增或以乐观锁更新一项策略。
     */
    @Transactional
    public GatewayPolicy save(PolicyScopeType scopeType, String scopeId, PolicyType policyType, PolicyChange change) {
        String normalizedScopeId = normalizeScopeId(scopeType, scopeId);
        String configJson = serializeAndValidate(policyType, change);
        GatewayPolicy current = find(scopeType, normalizedScopeId, policyType);
        if (current == null) {
            GatewayPolicy policy = new GatewayPolicy();
            policy.setScopeType(scopeType);
            policy.setScopeId(normalizedScopeId);
            policy.setPolicyType(policyType);
            policy.setMode(change.mode());
            policy.setConfigJson(configJson);
            policy.setLockVersion(0);
            mapper.insert(policy);
            return policy;
        }
        if (change.lockVersion() == null || !change.lockVersion().equals(current.getLockVersion())) {
            throw new IllegalStateException("策略已被其他人修改，请刷新后重试");
        }
        current.setMode(change.mode());
        current.setConfigJson(configJson);
        if (mapper.updateById(current) != 1) {
            throw new IllegalStateException("策略已被其他人修改，请刷新后重试");
        }
        return mapper.selectById(current.getId());
    }

    /**
     * 每一种策略独立解析。API 未作决定时只继承应用同类型策略，再继承全局同类型策略。
     */
    public EffectivePolicy resolve(PolicyType policyType, String serviceId, String apiId) {
        if (apiId != null && !apiId.isBlank()) {
            GatewayPolicy apiPolicy = find(PolicyScopeType.API, apiId, policyType);
            if (isDecision(apiPolicy)) {
                return toEffective(apiPolicy);
            }
        }
        if (serviceId != null && !serviceId.isBlank()) {
            GatewayPolicy applicationPolicy = find(PolicyScopeType.APPLICATION, serviceId, policyType);
            if (isDecision(applicationPolicy)) {
                return toEffective(applicationPolicy);
            }
        }
        GatewayPolicy globalPolicy = find(PolicyScopeType.GLOBAL, GLOBAL_SCOPE_ID, policyType);
        if (isDecision(globalPolicy)) {
            return toEffective(globalPolicy);
        }
        return new EffectivePolicy(policyType, PolicyMode.DISABLED, null, null, null, null);
    }

    private GatewayPolicy find(PolicyScopeType scopeType, String scopeId, PolicyType policyType) {
        return mapper.selectOne(new LambdaQueryWrapper<GatewayPolicy>()
                .eq(GatewayPolicy::getScopeType, scopeType)
                .eq(GatewayPolicy::getScopeId, scopeId)
                .eq(GatewayPolicy::getPolicyType, policyType));
    }

    private boolean isDecision(GatewayPolicy policy) {
        return policy != null && policy.getMode() != PolicyMode.INHERIT;
    }

    private EffectivePolicy toEffective(GatewayPolicy policy) {
        Object config = policy.getMode() == PolicyMode.ENABLED
                ? deserialize(policy.getPolicyType(), policy.getConfigJson()) : null;
        return new EffectivePolicy(policy.getPolicyType(), policy.getMode(), config,
                policy.getScopeType(), policy.getScopeId(), policy.getLockVersion());
    }

    private String serializeAndValidate(PolicyType policyType, PolicyChange change) {
        if (change.mode() != PolicyMode.ENABLED) {
            ensureNoConfig(change);
            return null;
        }
        Object config = selectedConfig(policyType, change);
        if (config == null) {
            throw new IllegalArgumentException(policyType + " 启用时必须提供对应配置");
        }
        ensureOnlySelectedConfig(policyType, change);
        Set<ConstraintViolation<Object>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getPropertyPath() + " "
                    + violations.iterator().next().getMessage());
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("策略配置无法序列化", exception);
        }
    }

    private Object selectedConfig(PolicyType policyType, PolicyChange change) {
        return switch (policyType) {
            case RATE_LIMIT -> change.rateLimit();
            case TIMEOUT -> change.timeout();
            case CIRCUIT_BREAKER -> change.circuitBreaker();
            case ACCESS_CONTROL -> change.accessControl();
        };
    }

    private void ensureOnlySelectedConfig(PolicyType policyType, PolicyChange change) {
        int configured = (change.rateLimit() == null ? 0 : 1)
                + (change.timeout() == null ? 0 : 1)
                + (change.circuitBreaker() == null ? 0 : 1)
                + (change.accessControl() == null ? 0 : 1);
        if (configured != 1 || selectedConfig(policyType, change) == null) {
            throw new IllegalArgumentException("只能提供与 policyType 对应的一项配置");
        }
    }

    private void ensureNoConfig(PolicyChange change) {
        if (change.rateLimit() != null || change.timeout() != null || change.circuitBreaker() != null
                || change.accessControl() != null) {
            throw new IllegalArgumentException("INHERIT 或 DISABLED 模式不能携带策略参数");
        }
    }

    private Object deserialize(PolicyType policyType, String configJson) {
        Class<?> type = switch (policyType) {
            case RATE_LIMIT -> RateLimitPolicyConfig.class;
            case TIMEOUT -> TimeoutPolicyConfig.class;
            case CIRCUIT_BREAKER -> CircuitBreakerPolicyConfig.class;
            case ACCESS_CONTROL -> AccessControlPolicyConfig.class;
        };
        try {
            return objectMapper.readValue(configJson, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的策略配置无法解析：" + policyType, exception);
        }
    }

    private String normalizeScopeId(PolicyScopeType scopeType, String scopeId) {
        if (scopeType == PolicyScopeType.GLOBAL) {
            return GLOBAL_SCOPE_ID;
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException(scopeType + " 策略必须提供 scopeId");
        }
        return scopeId;
    }
}
