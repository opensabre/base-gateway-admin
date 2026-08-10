package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.publication.dao.GatewayApiPublicationMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApplicationRouteMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayConfigVersionMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayReleaseMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayReleaseItemMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayInstanceRevisionMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayRouteProbeMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayConfigVersion;
import io.github.opensabre.gateway.admin.publication.model.GatewayRelease;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseResult;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseStatus;
import io.github.opensabre.gateway.admin.publication.model.ReleaseValidationResult;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceVerification;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeSummary;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseItem;
import io.github.opensabre.gateway.admin.publication.model.PublicationStatus;
import io.github.opensabre.gateway.admin.route.model.GatewayManagedPublishResult;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import io.github.opensabre.gateway.admin.policy.service.GlobalRuleCompilation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 正式发布的成功、CAS 冲突和发布记录状态测试。 */
class GatewayReleaseServiceTest {

    private static final GlobalRuleCompilation NO_GLOBAL_RULE_CHANGES =
            new GlobalRuleCompilation(false, List.of(), false, Map.of(), false);

    private final GatewayReleaseValidationService validationService = mock(GatewayReleaseValidationService.class);
    private final IGatewayRouteConfigService routeConfigService = mock(IGatewayRouteConfigService.class);
    private final GatewayReleaseMapper releaseMapper = mock(GatewayReleaseMapper.class);
    private final GatewayConfigVersionMapper versionMapper = mock(GatewayConfigVersionMapper.class);
    private final GatewayApiPublicationMapper apiMapper = mock(GatewayApiPublicationMapper.class);
    private final GatewayApplicationRouteMapper applicationMapper = mock(GatewayApplicationRouteMapper.class);
    private final GatewayReleaseItemMapper releaseItemMapper = mock(GatewayReleaseItemMapper.class);
    private final GatewayInstanceRevisionMapper instanceRevisionMapper = mock(GatewayInstanceRevisionMapper.class);
    private final GatewayInstanceVerificationService verificationService = mock(GatewayInstanceVerificationService.class);
    private final GatewayRouteProbeMapper routeProbeMapper = mock(GatewayRouteProbeMapper.class);
    private final GatewayRouteProbeService routeProbeService = mock(GatewayRouteProbeService.class);
    private GatewayReleaseService service;

    @BeforeEach
    void setUp() {
        service = new GatewayReleaseService(validationService, routeConfigService, releaseMapper,
                versionMapper, apiMapper, applicationMapper, releaseItemMapper,
                instanceRevisionMapper, verificationService, routeProbeMapper, routeProbeService);
        when(releaseMapper.insert(any())).thenAnswer(invocation -> {
            ((GatewayRelease) invocation.getArgument(0)).setId("release-1");
            return 1;
        });
        when(releaseMapper.updateById(any())).thenReturn(1);
        when(apiMapper.selectList(any())).thenReturn(List.of());
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(versionMapper.selectCount(any())).thenReturn(0L);
        when(versionMapper.insert(any())).thenReturn(1);
        when(releaseItemMapper.insert(any())).thenReturn(1);
        when(validationService.validate("base-v1"))
                .thenReturn(new ReleaseValidationResult("base-v1", 1, 0, List.of(), List.of(), Map.of(),
                        NO_GLOBAL_RULE_CHANGES));
        when(verificationService.verify(any())).thenReturn(new GatewayInstanceVerification(1, 1, List.of()));
        when(routeConfigService.managedRouteIds(any())).thenReturn(List.of());
        when(routeProbeService.probe(any(), any())).thenReturn(new GatewayRouteProbeSummary(1, 1, List.of()));
    }

    @Test
    void shouldPublishAndRecordImmutableVersion() {
        when(routeConfigService.publishManaged("base-v1", "release-1", List.of(), List.of(), Map.of(), NO_GLOBAL_RULE_CHANGES))
                .thenReturn(new GatewayManagedPublishResult("base-v1", "target-v2", "spring: {}"));

        GatewayReleaseResult result = service.publish("base-v1");

        assertThat(result.releaseId()).isEqualTo("release-1");
        assertThat(result.targetVersion()).isEqualTo("target-v2");
        assertThat(result.status()).isEqualTo(GatewayReleaseStatus.SUCCEEDED);
        verify(versionMapper).insert(any());
    }

    @Test
    void shouldRecordAndFinalizePendingApiOffline() {
        GatewayApiPublication offline = new GatewayApiPublication();
        offline.setId("publication-1");
        offline.setApiId("api-1");
        offline.setExternalPath("/orders/{id}");
        offline.setStatus(PublicationStatus.OFFLINE);
        when(apiMapper.selectList(any())).thenReturn(List.of(offline), List.of(), List.of(offline));
        when(apiMapper.updateById(offline)).thenReturn(1);
        when(routeConfigService.publishManaged("base-v1", "release-1", List.of(), List.of(), Map.of(), NO_GLOBAL_RULE_CHANGES))
                .thenReturn(new GatewayManagedPublishResult("base-v1", "target-v2", "spring: {}"));

        service.publish("base-v1");

        ArgumentCaptor<GatewayReleaseItem> item = ArgumentCaptor.forClass(GatewayReleaseItem.class);
        verify(releaseItemMapper).insert(item.capture());
        assertThat(item.getValue().getChangeType()).isEqualTo("OFFLINE");
        assertThat(item.getValue().getItemId()).isEqualTo("api-1");
        assertThat(offline.getPublishedVersion()).isEqualTo("target-v2");
    }

    @Test
    void shouldRecordAndFinalizePendingApplicationRouteOffline() {
        GatewayApplicationRoute offline = new GatewayApplicationRoute();
        offline.setId("route-1");
        offline.setExternalPath("/orders/**");
        offline.setStatus(PublicationStatus.OFFLINE);
        when(applicationMapper.selectList(any())).thenReturn(List.of(offline), List.of(), List.of(offline));
        when(applicationMapper.updateById(offline)).thenReturn(1);
        when(routeConfigService.publishManaged("base-v1", "release-1", List.of(), List.of(), Map.of(), NO_GLOBAL_RULE_CHANGES))
                .thenReturn(new GatewayManagedPublishResult("base-v1", "target-v2", "spring: {}"));

        service.publish("base-v1");

        ArgumentCaptor<GatewayReleaseItem> item = ArgumentCaptor.forClass(GatewayReleaseItem.class);
        verify(releaseItemMapper).insert(item.capture());
        assertThat(item.getValue().getItemType()).isEqualTo("APPLICATION_ROUTE");
        assertThat(item.getValue().getChangeType()).isEqualTo("OFFLINE");
        assertThat(item.getValue().getItemId()).isEqualTo("route-1");
        assertThat(offline.getPublishedVersion()).isEqualTo("target-v2");
    }

    @Test
    void shouldRecordFailedWhenCasPublishFails() {
        when(routeConfigService.publishManaged("base-v1", "release-1", List.of(), List.of(), Map.of(), NO_GLOBAL_RULE_CHANGES))
                .thenThrow(new IllegalStateException("网关配置已被其他人修改"));

        assertThatIllegalStateException().isThrownBy(() -> service.publish("base-v1"));

        ArgumentCaptor<GatewayRelease> captor = ArgumentCaptor.forClass(GatewayRelease.class);
        verify(releaseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GatewayReleaseStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("其他人修改");
    }

    @Test
    void shouldRollbackHistoricalSnapshotWithCurrentCasVersion() {
        GatewayRelease source = new GatewayRelease();
        source.setId("source-release");
        source.setTargetVersion("historical-v1");
        GatewayConfigVersion version = new GatewayConfigVersion();
        version.setVersion("historical-v1");
        version.setContent("spring: {}\n");
        when(releaseMapper.selectById("source-release")).thenReturn(source);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(routeConfigService.publishSnapshot("current-v3", "release-1", "spring: {}\n"))
                .thenReturn(new GatewayManagedPublishResult("current-v3", "historical-v1", "spring: {}\n"));

        GatewayReleaseResult result = service.rollback("source-release", "current-v3");

        assertThat(result.sourceVersion()).isEqualTo("current-v3");
        assertThat(result.targetVersion()).isEqualTo("historical-v1");
        assertThat(result.status()).isEqualTo(GatewayReleaseStatus.SUCCEEDED);
        verify(releaseItemMapper).insert(any());
    }

    @Test
    void shouldRejectRollbackWithoutImmutableVersion() {
        GatewayRelease source = new GatewayRelease();
        source.setTargetVersion("missing-version");
        when(releaseMapper.selectById("source-release")).thenReturn(source);
        when(versionMapper.selectOne(any())).thenReturn(null);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.rollback("source-release", "current-v3"))
                .withMessageContaining("缺少不可变配置版本");
    }

    @Test
    void shouldKeepReleasePartiallyAppliedUntilAllInstancesLoadRevision() {
        when(routeConfigService.publishManaged("base-v1", "release-1", List.of(), List.of(), Map.of(), NO_GLOBAL_RULE_CHANGES))
                .thenReturn(new GatewayManagedPublishResult("base-v1", "target-v2", "spring: {}"));
        when(verificationService.verify("release-1"))
                .thenReturn(new GatewayInstanceVerification(2, 1, List.of()));

        GatewayReleaseResult result = service.publish("base-v1");

        assertThat(result.status()).isEqualTo(GatewayReleaseStatus.PARTIALLY_APPLIED);
    }
}
