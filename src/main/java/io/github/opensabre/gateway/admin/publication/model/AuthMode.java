package io.github.opensabre.gateway.admin.publication.model;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** 路由对外暴露后的认证授权模式。 */
@OpenSabreDictionary(code = "gateway_auth_mode", name = "网关访问控制")
public enum AuthMode implements DictionaryEnum {
    PUBLIC("公开访问"), AUTHENTICATED("登录后访问"), RESOURCE_REQUIRED("资源授权");
    private final String label;
    AuthMode(String label) { this.label = label; }
    public String value() { return name(); }
    public String label() { return label; }
}
