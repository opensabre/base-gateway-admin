package io.github.opensabre.gateway.admin.policy.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 本级策略对上级策略的处理方式。 */
@OpenSabreDictionary(code = "gateway_policy_mode", name = "网关策略模式")
public enum PolicyMode implements DictionaryEnum {
    INHERIT("继承默认"), ENABLED("本级启用"), DISABLED("本级禁用");
    private final String label; PolicyMode(String label) { this.label = label; }
    public String value() { return name(); } public String label() { return label; }
}
