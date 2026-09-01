package io.github.opensabre.gateway.admin.integration;

import io.github.opensabre.common.core.entity.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Reads the authoritative application-to-product mapping from Organization. */
@FeignClient(name = "${opensabre.gateway-admin.organization-service-name:base-organization}",
        contextId = "gatewayOrganizationProductClient")
public interface OrganizationProductClient {

    /** Resolves the product owning a registered application. */
    @GetMapping("/products/applications/{application}/product-code")
    Result<String> getProductCode(@PathVariable("application") String application);
}
