package io.github.opensabre.gateway.admin.api.model;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** API 在最近一次成功发现中的状态。 */
@OpenSabreDictionary(code = "gateway_api_discovery_status", name = "网关 API 发现状态")
public enum ApiDiscoveryStatus implements DictionaryEnum {
    ACTIVE("正常", "S"),
    MISSING("已失联", "W");

    private final String label;
    private final String tagType;
    ApiDiscoveryStatus(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); }
    public String label() { return label; }
    public String tagType() { return tagType; }
}
