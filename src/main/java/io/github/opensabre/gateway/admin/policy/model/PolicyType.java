package io.github.opensabre.gateway.admin.policy.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 网关支持的治理策略类型。 */
@OpenSabreDictionary(code = "gateway_policy_type", name = "网关策略类型")
public enum PolicyType implements DictionaryEnum {
    RATE_LIMIT("限流"), TIMEOUT("超时"), CIRCUIT_BREAKER("熔断"), ACCESS_CONTROL("IP 黑白名单"),
    SECURITY_HEADERS("安全响应头"), DEFAULT_FILTERS("全局过滤器"), CORS("跨域规则");
    private final String label; PolicyType(String label) { this.label = label; }
    public String value() { return name(); } public String label() { return label; }
}
