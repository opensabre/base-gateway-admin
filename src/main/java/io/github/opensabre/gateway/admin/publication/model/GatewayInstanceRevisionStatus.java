package io.github.opensabre.gateway.admin.publication.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 单个网关实例对本次发布的加载状态。 */
@OpenSabreDictionary(code = "gateway_instance_revision_status", name = "网关实例加载状态")
public enum GatewayInstanceRevisionStatus implements DictionaryEnum {
    LOADED("已加载", "S"), PENDING("等待加载", "W"), UNREACHABLE("不可达", "D");
    private final String label; private final String tagType;
    GatewayInstanceRevisionStatus(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); } public String label() { return label; } public String tagType() { return tagType; }
}
