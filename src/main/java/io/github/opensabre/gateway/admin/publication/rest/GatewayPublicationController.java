package io.github.opensabre.gateway.admin.publication.rest;

import io.github.opensabre.gateway.admin.publication.model.ApiPublicationChange;
import io.github.opensabre.gateway.admin.publication.model.ApplicationRouteChange;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.ReleaseValidationRequest;
import io.github.opensabre.gateway.admin.publication.model.ReleaseValidationResult;
import io.github.opensabre.gateway.admin.publication.model.GatewayRelease;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseDetail;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseResult;
import io.github.opensabre.gateway.admin.publication.model.GatewayRollbackRequest;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceVerification;
import io.github.opensabre.gateway.admin.publication.model.OfflineRequest;
import io.github.opensabre.gateway.admin.publication.model.LegacyRouteAdoptionRequest;
import io.github.opensabre.gateway.admin.publication.service.GatewayPublicationService;
import io.github.opensabre.gateway.admin.publication.service.GatewayReleaseService;
import io.github.opensabre.gateway.admin.publication.service.GatewayReleaseValidationService;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 发布声明和应用级路由草稿管理接口。 */
@Tag(name = "网关发布草稿")
@RestController
@RequestMapping
public class GatewayPublicationController {

    private final GatewayPublicationService service;
    private final GatewayReleaseValidationService validationService;
    private final GatewayReleaseService releaseService;

    public GatewayPublicationController(GatewayPublicationService service,
            GatewayReleaseValidationService validationService,
            GatewayReleaseService releaseService) {
        this.service = service;
        this.validationService = validationService;
        this.releaseService = releaseService;
    }

    @GetMapping("/api-publications")
    @Operation(summary = "查询 API 发布声明")
    public List<GatewayApiPublication> listApiPublications() {
        return service.listApiPublications();
    }

    /** 仅保存草稿，不直接写入 Nacos。 */
    @PutMapping("/apis/{apiId}/publication")
    @Operation(summary = "保存 API 发布草稿")
    @Audit(operationType = OperationType.UPDATE, description = "保存 API 发布草稿",
            module = "GATEWAY_API_PUBLICATION", response = true, key = "#apiId")
    public GatewayApiPublication saveApiDraft(@PathVariable String apiId,
            @Valid @RequestBody ApiPublicationChange change) {
        return service.saveApiDraft(apiId, change);
    }

    /** 仅生成待下线声明，需通过发布中心正式生效。 */
    @PostMapping("/apis/{apiId}/offline")
    @Operation(summary = "将 API 标记为待下线")
    @Audit(operationType = OperationType.UPDATE, description = "标记 API 待下线",
            module = "GATEWAY_API_PUBLICATION", response = true, key = "#apiId")
    public GatewayApiPublication offlineApi(@PathVariable String apiId,
            @Valid @RequestBody OfflineRequest request) {
        return service.offlineApi(apiId, request.lockVersion());
    }

    @GetMapping("/application-routes")
    @Operation(summary = "查询应用级路由")
    public List<GatewayApplicationRoute> listApplicationRoutes() {
        return service.listApplicationRoutes();
    }

    @PostMapping("/application-routes")
    @Operation(summary = "新增应用级路由草稿")
    @Audit(operationType = OperationType.CREATE, description = "新增应用级路由草稿",
            module = "GATEWAY_APPLICATION_ROUTE", response = true)
    public GatewayApplicationRoute createApplicationDraft(
            @Valid @RequestBody ApplicationRouteChange change) {
        return service.createApplicationDraft(change);
    }

    @PostMapping("/application-routes/adopt")
    @Operation(summary = "将非托管运行时路由导入为应用路由草稿")
    @Audit(operationType = OperationType.CREATE, description = "导入遗留网关路由",
            module = "GATEWAY_APPLICATION_ROUTE", response = true, key = "#request.routeId")
    public GatewayApplicationRoute adoptLegacyRoute(
            @Valid @RequestBody LegacyRouteAdoptionRequest request) {
        return service.adoptLegacyRoute(request.routeId(), request.baseVersion());
    }

    @PutMapping("/application-routes/{id}")
    @Operation(summary = "修改应用级路由草稿")
    @Audit(operationType = OperationType.UPDATE, description = "修改应用级路由草稿",
            module = "GATEWAY_APPLICATION_ROUTE", response = true, key = "#id")
    public GatewayApplicationRoute updateApplicationDraft(@PathVariable String id,
            @Valid @RequestBody ApplicationRouteChange change) {
        return service.updateApplicationDraft(id, change);
    }

    /** 仅生成应用路由待下线声明，需通过发布中心正式生效。 */
    @PostMapping("/application-routes/{id}/offline")
    @Operation(summary = "将应用级路由标记为待下线")
    @Audit(operationType = OperationType.UPDATE, description = "标记应用级路由待下线",
            module = "GATEWAY_APPLICATION_ROUTE", response = true, key = "#id")
    public GatewayApplicationRoute offlineApplicationRoute(@PathVariable String id,
            @Valid @RequestBody OfflineRequest request) {
        return service.offlineApplicationRoute(id, request.lockVersion());
    }

    /** 解析三级策略并生成候选配置，但不写入 Nacos。 */
    @PostMapping("/releases/validate")
    @Operation(summary = "校验网关发布候选")
    public ReleaseValidationResult validate(@Valid @RequestBody ReleaseValidationRequest request) {
        return validationService.validate(request.baseVersion());
    }

    /** 以当前候选执行 Nacos CAS 正式发布。 */
    @PostMapping("/releases")
    @Operation(summary = "发布网关候选配置")
    @Audit(operationType = OperationType.CREATE, description = "发布网关候选配置",
            module = "GATEWAY_RELEASE", response = true)
    public GatewayReleaseResult publish(@Valid @RequestBody ReleaseValidationRequest request) {
        return releaseService.publish(request.baseVersion());
    }

    @GetMapping("/releases")
    @Operation(summary = "查询网关发布历史")
    public List<GatewayRelease> listReleases() {
        return releaseService.list();
    }

    @GetMapping("/releases/{id}")
    @Operation(summary = "查询网关发布详情")
    public GatewayReleaseDetail getRelease(@PathVariable String id) {
        return releaseService.get(id);
    }

    @PostMapping("/releases/{id}/rollback")
    @Operation(summary = "回滚到历史网关配置")
    @Audit(operationType = OperationType.CREATE, description = "回滚历史网关配置",
            module = "GATEWAY_RELEASE", response = true, key = "#id")
    public GatewayReleaseResult rollback(@PathVariable String id,
            @Valid @RequestBody GatewayRollbackRequest request) {
        return releaseService.rollback(id, request.baseVersion());
    }

    @PostMapping("/releases/{id}/verify-instances")
    @Operation(summary = "重新确认网关实例加载状态")
    public GatewayInstanceVerification verifyInstances(@PathVariable String id) {
        return releaseService.verifyInstances(id);
    }
}
