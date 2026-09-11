package io.github.opensabre.gateway.admin.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class ReadClientUriTest {

    @Test
    void shouldBuildEncodedNacosReadUris() {
        GatewayIntegrationProperties.Nacos properties = new GatewayIntegrationProperties.Nacos();
        properties.setServerUrl("http://nacos:8848/");
        properties.setNamespace("dev space");
        properties.setGroup("GATEWAY GROUP");
        properties.setGatewayDataId("base gateway.yml");
        NacosReadClient client = new NacosReadClient(properties, HttpClient.newHttpClient());

        assertThat(client.serviceListUri(1, 100).toString())
                .isEqualTo("http://nacos:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&groupName=GATEWAY+GROUP&namespaceId=dev+space");
        assertThat(client.gatewayConfigUri().toString())
                .isEqualTo("http://nacos:8848/nacos/v1/cs/configs?dataId=base+gateway.yml&group=GATEWAY+GROUP&tenant=dev+space");
        assertThat(client.instanceListUri("base sysadmin").toString())
                .isEqualTo("http://nacos:8848/nacos/v1/ns/instance/list?serviceName=base+sysadmin&groupName=GATEWAY+GROUP&namespaceId=dev+space&healthyOnly=false");
    }
}
