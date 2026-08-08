package io.github.opensabre.gateway.admin.publication.model;
import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 通配路由审批预留状态；当前版本固定为 NOT_REQUIRED。 */
@OpenSabreDictionary(code = "gateway_approval_status", name = "网关审批状态")
public enum ApprovalStatus implements DictionaryEnum {
    NOT_REQUIRED("无需审批"), PENDING("待审批"), APPROVED("已通过"), REJECTED("已拒绝");
    private final String label; ApprovalStatus(String label) { this.label = label; }
    public String value() { return name(); } public String label() { return label; }
}
