package io.github.opensabre.gateway.admin.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
