package io.github.opensabre.gateway.admin.policy.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 网关支持的治理策略类型。 */
@OpenSabreDictionary(code = "gateway_policy_type", name = "网关策略类型")
public enum PolicyType implements DictionaryEnum {
    RATE_LIMIT("限流"), TIMEOUT("超时"), CIRCUIT_BREAKER("熔断");
    private final String label; PolicyType(String label) { this.label = label; }
    public String value() { return name(); } public String label() { return label; }
}
