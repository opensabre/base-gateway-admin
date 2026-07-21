# base-gateway-admin

OpenSabre 独立网关控制面。当前已迁入原 `base-sysadmin` 的路由、默认过滤器和 OAuth2/OIDC 配置能力，并提供 Nacos、Prometheus 只读集成及控制面数据结构。

迁移验证阶段 `GATEWAY_CONFIGURATION_WRITE_ENABLED` 默认为 `false`：读取接口可用于新旧结果比对，所有配置写接口会在调用 Nacos 前被拒绝。

## 本地启动

1. 执行 `src/main/resources/db/os-base-gateway-admin-ddl.sql`。
2. 配置 MySQL、Nacos 和 Prometheus 环境变量。
3. 运行 `mvn spring-boot:run`。

默认端口为 `8030`，健康检查为 `/actuator/health`，能力说明为 `/gateway-admin/info`。

## 路由控制面切换

1. 保持 `GATEWAY_CONFIGURATION_WRITE_ENABLED=false` 启动新应用。
2. 分别读取旧接口 `/sysadmin/gateway/routes` 与新接口 `/gateway-admin/routes`，确认版本和配置内容一致。
3. 设置 `GATEWAY_CONFIGURATION_WRITE_ENABLED=true` 并重启新应用。
4. 前端设置 `VITE_GATEWAY_ROUTE_API_BASE=/gateway-admin/routes` 后重新构建。
5. 验证增删改查及 Nacos 配置发布，再停止旧服务的网关路由写入口。
