package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.integration.OrganizationResourceClient;
import io.github.opensabre.common.core.entity.vo.Result;
import io.github.opensabre.gateway.admin.publication.model.AuthMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** 组织资源绑定必须与运行时授权使用的 Method + PathPattern 保持一致。 */
class GatewayResourceBindingValidatorTest {

    @Test
    void shouldAcceptEquivalentPathVariables() {
        OrganizationResourceClient client = id -> Result.success(new OrganizationResourceClient.OrganizationResource(
                id, "order:view", "resource", "/orders/{id}", "GET", "查看订单"));
        GatewayResourceBindingValidator validator = new GatewayResourceBindingValidator(client);

        validator.validate(AuthMode.RESOURCE_REQUIRED, "100", "GET", "/orders/{orderId}");
    }

    @Test
    void shouldRejectMethodOrPathMismatch() {
        OrganizationResourceClient client = id -> Result.success(new OrganizationResourceClient.OrganizationResource(
                id, "order:view", "resource", "/orders/{id}", "GET", "查看订单"));
        GatewayResourceBindingValidator validator = new GatewayResourceBindingValidator(client);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(AuthMode.RESOURCE_REQUIRED, "100", "POST", "/orders/{id}"))
                .withMessageContaining("Method");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(AuthMode.RESOURCE_REQUIRED, "100", "GET", "/orders/search"))
                .withMessageContaining("Path");
    }

    @Test
    void shouldFailClosedWhenOrganizationServiceIsUnavailable() {
        OrganizationResourceClient client = id -> { throw new IllegalStateException("unavailable"); };
        GatewayResourceBindingValidator validator = new GatewayResourceBindingValidator(client);

        assertThatIllegalStateException()
                .isThrownBy(() -> validator.validate(AuthMode.RESOURCE_REQUIRED, "100", "GET", "/orders/{id}"))
                .withMessageContaining("禁止保存或发布");
    }

    @Test
    void shouldRejectResourceBindingForNonResourceAuthModes() {
        GatewayResourceBindingValidator validator = new GatewayResourceBindingValidator(id -> Result.success(null));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(AuthMode.PUBLIC, "100", "GET", "/orders"))
                .withMessageContaining("只有 RESOURCE_REQUIRED");
    }
}
