package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.integration.GatewayRevisionReadClient;
import io.github.opensabre.gateway.admin.publication.dao.GatewayInstanceRevisionMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevisionStatus;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceVerification;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 网关实例加载修订号的汇总与异常隔离测试。 */
class GatewayInstanceVerificationServiceTest {

    @Test
    void shouldDistinguishLoadedPendingAndUnreachableInstances() {
        GatewayServiceCatalogService catalog = mock(GatewayServiceCatalogService.class);
        GatewayRevisionReadClient client = mock(GatewayRevisionReadClient.class);
        GatewayInstanceRevisionMapper mapper = mock(GatewayInstanceRevisionMapper.class);
        GatewayServiceInstance loaded = instance("10.0.0.1", 8443);
        GatewayServiceInstance pending = instance("10.0.0.2", 8443);
        GatewayServiceInstance unreachable = instance("10.0.0.3", 8443);
        when(catalog.getService("base-gateway")).thenReturn(
                new GatewayServiceSummary("base-gateway", 3, 3, List.of(loaded, pending, unreachable)));
        when(client.fetch(loaded)).thenReturn("release-7");
        when(client.fetch(pending)).thenReturn("release-6");
        when(client.fetch(unreachable)).thenThrow(new IllegalStateException("timeout"));
        when(mapper.insert(any(io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevision.class))).thenReturn(1);
        GatewayInstanceVerificationService service = new GatewayInstanceVerificationService(
                catalog, client, mapper, "base-gateway");

        GatewayInstanceVerification result = service.verify("release-7");

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.allLoaded()).isFalse();
        assertThat(result.instances()).extracting(item -> item.getStatus()).containsExactly(
                GatewayInstanceRevisionStatus.LOADED,
                GatewayInstanceRevisionStatus.PENDING,
                GatewayInstanceRevisionStatus.UNREACHABLE);
    }

    @Test
    void shouldWaitForGatewayToLoadPublishedRevision() {
        GatewayServiceCatalogService catalog = mock(GatewayServiceCatalogService.class);
        GatewayRevisionReadClient client = mock(GatewayRevisionReadClient.class);
        GatewayInstanceRevisionMapper mapper = mock(GatewayInstanceRevisionMapper.class);
        GatewayServiceInstance gateway = instance("10.0.0.1", 8443);
        when(catalog.getService("base-gateway")).thenReturn(
                new GatewayServiceSummary("base-gateway", 1, 1, List.of(gateway)));
        when(client.fetch(gateway)).thenReturn("release-6", "release-7");
        when(mapper.insert(any(io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevision.class))).thenReturn(1);
        GatewayInstanceVerificationService service = new GatewayInstanceVerificationService(
                catalog, client, mapper, "base-gateway", 3, 0);

        GatewayInstanceVerification result = service.verify("release-7");

        assertThat(result.allLoaded()).isTrue();
        verify(client, times(2)).fetch(gateway);
    }

    private GatewayServiceInstance instance(String ip, int port) {
        return new GatewayServiceInstance(ip, port, "DEFAULT", true, true, 1.0, Map.of());
    }
}
