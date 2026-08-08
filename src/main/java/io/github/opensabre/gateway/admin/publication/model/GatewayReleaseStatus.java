package io.github.opensabre.gateway.admin.publication.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 网关发布执行状态。 */
@OpenSabreDictionary(code = "gateway_release_status", name = "网关发布执行状态")
public enum GatewayReleaseStatus implements DictionaryEnum {
    PUBLISHING("发布中", "I"), SUCCEEDED("成功", "S"), PARTIALLY_APPLIED("部分生效", "W"), FAILED("失败", "D"), RECONCILIATION_REQUIRED("需要人工处理", "D");
    private final String label; private final String tagType;
    GatewayReleaseStatus(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); } public String label() { return label; } public String tagType() { return tagType; }
}
