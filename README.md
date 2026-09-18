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

路由新增、修改、下线统一通过 API/应用路由草稿和发布中心处理。旧的 `/routes` 直接新增、
修改、删除以及 `/routes/default-filters` 写接口不再暴露；`GET /routes` 保留为运行配置只读
快照，OAuth2 认证方式仍使用 `/routes/oauth2-clients`。

非托管运行时路由可调用 `POST /application-routes/adopt` 导入应用路由草稿。正式发布时，
控制面会在同一次 Nacos CAS 中移除旧 Route ID 并加入托管 Route，发布失败不会改变旧路由。

## IP 黑白名单

黑白名单复用分层策略和发布流程，在 `GLOBAL`、`APPLICATION`、`API` 作用域保存
`ACCESS_CONTROL` 策略。保存只产生草稿；执行发布后，控制面将有效策略编译为
`OpenSabreIpAccessControl` 路由过滤器并原子写入网关配置。

## 全局规则

全局过滤器和 CORS 复用同一策略、预检、版本、CAS 发布及回滚链路，分别保存为仅允许
`GLOBAL` 作用域的 `DEFAULT_FILTERS`、`CORS` 策略。全局过滤器草稿直接表达
`spring.cloud.gateway.default-filters` 的顺序、启停状态和参数，并始终保留启用的唯一
`TokenRelay`；CORS 编译到 `spring.cloud.gateway.globalcors`。

未保存全局过滤器策略时，管理端从当前 Nacos 配置导入编辑，不会因打开页面丢失现有项。
安全响应头通过 Filter 快捷模板生成，仍可逐项修改和删除。
数据库升级需执行 `db/migrations/V20260809_01__expand_gateway_policy_config.sql`。

## 运行监控

应用实例的 Actuator 基础指标通过 OpenSabre 内部 Token 获取。管理端按服务发现名称签发
短期、限定 audience 且仅含 `ACTUATOR_METRICS_READ` 权限的 Token；各应用不会匿名放行
这些指标，也无需维护独立的 Actuator 用户名和密码。

`GET /monitoring/routes` 只执行服务端定义的 PromQL，返回按 Route ID 聚合的最近 5 分钟
请求率、5xx 错误率和 P95 延迟原始快照。接口不接受客户端 PromQL，避免把控制面变成任意
Prometheus 查询代理。

Prometheus 查询属于本控制面，不属于应用侧 Framework。`PROMETHEUS_URL` 默认指向
`http://localhost:9090`，容器部署应配置为 `http://prometheus:9090`。

- `GET /monitoring/status` 返回 Prometheus 可用状态和各采集目标的 `up` 向量。
- `GET /monitoring/routes/history?range=1h` 返回路由 TPS、5xx TPS、P50/P95/P99 时序。
- `GET /monitoring/applications/history?range=1h&application=base-sysadmin` 返回应用 TPS、延迟、CPU
  和堆内存时序。

时间范围只接受 `15m`、`1h`、`6h`、`24h`、`7d`、`30d`，查询步长由服务端确定；路由、
应用和实例参数只作为经过校验的标签值使用，客户端不能提交任意 PromQL。
