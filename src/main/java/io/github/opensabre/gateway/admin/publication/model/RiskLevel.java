package io.github.opensabre.gateway.admin.publication.model;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 路由暴露范围风险等级。 */
@OpenSabreDictionary(code = "gateway_risk_level", name = "网关风险等级")
public enum RiskLevel implements DictionaryEnum {
    LOW("低风险", "S"), MEDIUM("中风险", "W"), HIGH("高风险", "D");
    private final String label; private final String tagType;
    RiskLevel(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); }
    public String label() { return label; }
    public String tagType() { return tagType; }
}
