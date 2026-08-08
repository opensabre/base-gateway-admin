package io.github.opensabre.gateway.admin.publication.model;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** API 或应用路由声明状态。 */
@OpenSabreDictionary(code = "gateway_publication_status", name = "网关发布状态")
public enum PublicationStatus implements DictionaryEnum {
    DRAFT("草稿", "I"), PUBLISHED("已发布", "S"), OFFLINE("待下线", "W");
    private final String label; private final String tagType;
    PublicationStatus(String label, String tagType) { this.label = label; this.tagType = tagType; }
    public String value() { return name(); }
    public String label() { return label; }
    public String tagType() { return tagType; }
}
