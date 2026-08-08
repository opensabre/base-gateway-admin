# base-gateway-admin

OpenSabre 独立网关控制面。原 `base-sysadmin` 的路由、默认过滤器和 OAuth2/OIDC 配置能力已迁入本服务，并提供 Nacos、Prometheus 集成及控制面数据结构。

本服务是网关配置的唯一控制面，`GATEWAY_CONFIGURATION_WRITE_ENABLED` 默认为 `true`。紧急情况下可将其设为 `false`，所有配置写接口会在调用 Nacos 前被拒绝，查询不受影响。

## 本地启动

1. 执行 `src/main/resources/db/os-base-gateway-admin-ddl.sql`。
2. 配置 MySQL、Nacos 和 Prometheus 环境变量。
3. 运行 `mvn spring-boot:run`。

默认端口为 `8030`，服务内部健康检查为 `/actuator/health`、能力说明为 `/info`；经 `gateway-web` 暴露的能力说明地址为 `/gateway-admin/info`。

## 路由控制面验证

1. 确认前端使用 `/gateway-admin/routes`，且 `base-sysadmin` 不再暴露网关管理接口。
2. 读取 `/gateway-admin/routes`，确认版本和配置内容与 Nacos 中的 `base-gateway.yml` 一致。
3. 使用当前版本执行一次无语义变化的 CAS 发布，确认写入、审计和版本控制正常。
4. 验证新增、修改、删除及网关运行时生效；验证后恢复原始配置。
5. 如需紧急冻结配置写入，设置 `GATEWAY_CONFIGURATION_WRITE_ENABLED=false` 并重启本服务。
