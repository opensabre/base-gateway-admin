package io.github.opensabre.gateway.admin.policy.rest;

import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.GatewayPolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyChange;
import io.github.opensabre.gateway.admin.policy.model.PolicyScopeType;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.service.GatewayPolicyService;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 网关三级治理策略接口。 */
@Tag(name = "网关治理策略")
@RestController
@RequestMapping("/policies")
public class GatewayPolicyController {

    private final GatewayPolicyService service;

    public GatewayPolicyController(GatewayPolicyService service) {
        this.service = service;
    }

    /** 查询指定作用域的本级策略。 */
    @GetMapping
    @Operation(summary = "查询本级网关治理策略")
    public List<GatewayPolicy> list(@RequestParam PolicyScopeType scopeType,
            @RequestParam(required = false) String scopeId) {
        return service.list(scopeType, scopeId);
    }

    /** 保存指定作用域的一项类型化策略。 */
    @PutMapping
    @Operation(summary = "保存网关治理策略")
    @Audit(operationType = OperationType.UPDATE, description = "保存网关治理策略",
            module = "GATEWAY_POLICY", response = true,
            key = "#scopeType + ':' + (#scopeId == null ? 'GLOBAL' : #scopeId) + ':' + #policyType")
    public GatewayPolicy save(@RequestParam PolicyScopeType scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam PolicyType policyType,
            @Valid @RequestBody PolicyChange change) {
        return service.save(scopeType, scopeId, policyType, change);
    }

    /** 查询某个 API 在三级继承后的最终策略。 */
    @GetMapping("/effective")
    @Operation(summary = "查询最终生效的网关治理策略")
    public EffectivePolicy effective(@RequestParam PolicyType policyType,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String apiId) {
        return service.resolve(policyType, serviceId, apiId);
    }
}
