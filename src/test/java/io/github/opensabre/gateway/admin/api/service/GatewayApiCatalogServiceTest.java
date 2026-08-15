package io.github.opensabre.gateway.admin.api.service;

import tools.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.opensabre.gateway.admin.api.dao.GatewayApiMapper;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayApiCatalogServiceTest {

    private final GatewayApiCatalogService service = new GatewayApiCatalogService(
            null, null, null, new ObjectMapper());

    @Test
    void parsesOnlyHttpOperationsWithStableIdentity() {
        var result = service.parseOpenApi("""
                {
                  "openapi": "3.0.1",
                  "paths": {
                    "/users/{id}": {
                      "parameters": [],
                      "get": {
                        "operationId": "getUser",
                        "summary": "Get user",
                        "tags": ["users"]
                      },
                      "delete": {
                        "operationId": "deleteUser"
                      }
                    }
                  }
                }
                """);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GatewayApiCatalogService.DiscoveredApi::httpMethod)
                .containsExactly("GET", "DELETE");
        assertThat(result.get(0).path()).isEqualTo("/users/{id}");
        assertThat(result.get(0).sourceHash()).hasSize(64);
    }

    @Test
    void rejectsNonOpenApiDocumentBeforeItCanBeApplied() {
        assertThatThrownBy(() -> service.parseOpenApi("{\"paths\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OpenAPI 3");
    }

    @Test
    void rejectsMalformedJsonBeforeItCanBeApplied() {
        assertThatThrownBy(() -> service.parseOpenApi("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效 JSON");
    }

    @Test
    void pagesApiAssetsAndCapsPageSize() {
        GatewayApiMapper mapper = mock(GatewayApiMapper.class);
        GatewayApiCatalogService catalog = new GatewayApiCatalogService(
                mapper, null, null, new ObjectMapper());
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<GatewayApi> requested = invocation.getArgument(0);
            return new Page<GatewayApi>(requested.getCurrent(), requested.getSize(), 42)
                    .setRecords(List.of(new GatewayApi()));
        });

        var result = catalog.list("base-sysadmin", null, 0, 1000);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(200);
        assertThat(result.total()).isEqualTo(42);
        assertThat(result.apis()).hasSize(1);
    }
}
