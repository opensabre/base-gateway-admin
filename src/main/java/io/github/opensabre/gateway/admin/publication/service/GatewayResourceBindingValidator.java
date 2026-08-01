package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.integration.OrganizationResourceClient;
import io.github.opensabre.common.core.entity.vo.Result;
import io.github.opensabre.gateway.admin.publication.model.AuthMode;
import org.springframework.stereotype.Component;

/** 按网关运行时相同的 Method + PathPattern 语义校验组织资源绑定。 */
@Component
public class GatewayResourceBindingValidator {

    private final OrganizationResourceClient resourceClient;

    public GatewayResourceBindingValidator(OrganizationResourceClient resourceClient) {
        this.resourceClient = resourceClient;
    }

    /** RESOURCE_REQUIRED 必须绑定真实且与外部入口一致的 API 资源。 */
    public void validate(AuthMode authMode, String resourceId, String method, String externalPath) {
        if (authMode != AuthMode.RESOURCE_REQUIRED) {
            if (resourceId != null && !resourceId.isBlank()) {
                throw new IllegalArgumentException("只有 RESOURCE_REQUIRED API 可以关联组织资源");
            }
            return;
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("RESOURCE_REQUIRED API 必须关联组织资源");
        }
        OrganizationResourceClient.OrganizationResource resource;
        try {
            Result<OrganizationResourceClient.OrganizationResource> response = resourceClient.get(resourceId);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("组织资源查询返回失败");
            }
            resource = response.getData();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法确认组织资源，禁止保存或发布受保护 API", exception);
        }
        if (resource == null || resource.id() == null || resource.id().isBlank()) {
            throw new IllegalArgumentException("关联的组织资源不存在：" + resourceId);
        }
        if (!method.equalsIgnoreCase(resource.method())) {
            throw new IllegalArgumentException("组织资源 HTTP Method 与 API 外部入口不一致");
        }
        if (!normalizePattern(externalPath).equals(normalizePattern(resource.url()))) {
            throw new IllegalArgumentException("组织资源 URL 与 API 外部 Path 不一致");
        }
    }

    private String normalizePattern(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\{[^/{}]+}", "{}");
    }
}
