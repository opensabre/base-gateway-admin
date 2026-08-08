package io.github.opensabre.gateway.admin.api.model;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** API 资产来源。 */
@OpenSabreDictionary(code = "gateway_api_source_type", name = "网关 API 来源")
public enum ApiSourceType implements DictionaryEnum {
    OPENAPI("OpenAPI 同步"), MANUAL("手工维护");
    private final String label;
    ApiSourceType(String label) { this.label = label; }
    public String value() { return name(); }
    public String label() { return label; }
}
