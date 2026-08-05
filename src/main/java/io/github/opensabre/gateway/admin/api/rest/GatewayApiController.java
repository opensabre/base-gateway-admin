package io.github.opensabre.gateway.admin.api.rest;

import io.github.opensabre.gateway.admin.api.model.ApiDiscoveryStatus;
import io.github.opensabre.gateway.admin.api.model.ApiSyncResult;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.api.model.GatewayApiPage;
import io.github.opensabre.gateway.admin.api.service.GatewayApiCatalogService;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 资产查询与 OpenAPI 同步接口。 */
@Tag(name = "网关 API 资产")
@RestController
public class GatewayApiController {

    private final GatewayApiCatalogService service;

    public GatewayApiController(GatewayApiCatalogService service) {
        this.service = service;
    }

    /** 查询已发现的 API 资产。 */
    @GetMapping("/apis")
    @Operation(summary = "查询网关 API 资产")
    public GatewayApiPage list(@RequestParam(required = false) String serviceId,
            @RequestParam(required = false) ApiDiscoveryStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return service.list(serviceId, status, page, pageSize);
    }

    /** 从健康服务实例同步 OpenAPI，不会自动对外发布。 */
    @PostMapping("/services/{serviceId}/apis/sync")
    @Operation(summary = "同步服务 OpenAPI")
    @Audit(operationType = OperationType.UPDATE, description = "同步服务 OpenAPI",
            module = "GATEWAY_API", response = true, key = "#serviceId")
    public ApiSyncResult sync(@PathVariable String serviceId) {
        return service.sync(serviceId);
    }
}
