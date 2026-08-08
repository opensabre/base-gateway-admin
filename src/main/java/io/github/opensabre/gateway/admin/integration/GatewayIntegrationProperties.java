package io.github.opensabre.gateway.admin.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关控制面集成配置。
 */
@ConfigurationProperties(prefix = "opensabre.gateway-admin")
public class GatewayIntegrationProperties {

    private boolean configurationWriteEnabled = true;
    private final Nacos nacos = new Nacos();
    private final Prometheus prometheus = new Prometheus();

    public boolean isConfigurationWriteEnabled() { return configurationWriteEnabled; }
    public void setConfigurationWriteEnabled(boolean configurationWriteEnabled) {
        this.configurationWriteEnabled = configurationWriteEnabled;
    }
    public Nacos getNacos() { return nacos; }
    public Prometheus getPrometheus() { return prometheus; }

    /** Nacos 服务发现和配置查询参数。 */
    public static class Nacos {
        private String serverUrl = "http://localhost:8848";
        private String namespace = "";
        private String group = "DEFAULT_GROUP";
        private String gatewayDataId = "base-gateway.yml";

        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getGatewayDataId() { return gatewayDataId; }
        public void setGatewayDataId(String gatewayDataId) { this.gatewayDataId = gatewayDataId; }
    }

    /** Prometheus 查询端点。 */
    public static class Prometheus {
        private String serverUrl = "http://localhost:9090";

        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    }
}
