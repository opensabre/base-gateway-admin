package io.github.opensabre.gateway.admin.service.rest;

import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServicePage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 网关可用服务与实例的只读查询接口。 */
@Tag(name = "网关服务管理")
@RestController
@RequestMapping("/services")
public class GatewayServiceController {

    private final GatewayServiceCatalogService serviceCatalogService;

    public GatewayServiceController(GatewayServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    /** 分页查询 Nacos 服务及实例健康状态。 */
    @GetMapping
    @Operation(summary = "查询网关服务目录")
    public GatewayServicePage list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return serviceCatalogService.listServices(page, pageSize);
    }
}
