package io.github.opensabre.gateway.admin.publication.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 单实例托管路由装载探测状态。 */
@OpenSabreDictionary(code = "gateway_route_probe_status", name = "网关路由探测状态")
public enum GatewayRouteProbeStatus implements DictionaryEnum {
    PASSED("通过", "S"), MISSING("路由缺失", "W"), UNREACHABLE("实例不可达", "D");
    private final String label; private final String tagType;
    GatewayRouteProbeStatus(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); } public String label() { return label; } public String tagType() { return tagType; }
}
