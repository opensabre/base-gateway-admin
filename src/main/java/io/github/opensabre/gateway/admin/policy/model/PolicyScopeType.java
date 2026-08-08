package io.github.opensabre.gateway.admin.policy.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 网关治理策略作用域。 */
@OpenSabreDictionary(code = "gateway_policy_scope", name = "网关策略作用域")
public enum PolicyScopeType implements DictionaryEnum {
    GLOBAL("全局"), APPLICATION("应用"), API("API");
    private final String label; PolicyScopeType(String label) { this.label = label; }
    public String value() { return name(); } public String label() { return label; }
}
