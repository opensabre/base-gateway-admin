package io.github.opensabre.gateway.admin.integration;

import io.github.opensabre.common.core.entity.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 查询组织服务现有 API 资源；网关控制面不复制资源权限模型。 */
@FeignClient(name = "${opensabre.gateway-admin.organization-service-name:base-organization}",
        contextId = "gatewayOrganizationResourceClient")
public interface OrganizationResourceClient {

    @GetMapping("/resource/{id}")
    Result<OrganizationResource> get(@PathVariable("id") String id);

    /** 网关发布校验所需的最小资源投影。 */
    record OrganizationResource(String id, String code, String type, String url, String method, String name) {
    }
}
