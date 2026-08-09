# 网关 API 发布与分层治理设计

## 1. 文档状态

- 状态：Proposed
- 日期：2026-07-29
- 所属系统：`base-gateway-admin` 控制面、`base-gateway` 流量面、`opensabre-admin` 管理端
- 目标：将网关管理对象从单纯的 Spring Cloud Gateway Route，提升为可发现、可独立发布、可继承治理策略的 API 资产

## 2. 背景与现状

当前控制面已经能够读取 `base-gateway.yml`，编辑
`spring.cloud.gateway.routes` 和 `default-filters`，并通过 Nacos `casMd5`
发布完整配置。当前数据脚本也已经预留配置草稿、不可变版本、发布记录和实例生效记录。

现有能力存在以下边界：

1. 路由管理对象仍然是原始 Route。`Path=/api/auth/**` 可能一次暴露整个应用。
2. 服务发现路由当前开启，应用可能通过自动生成的服务路由被访问。
3. 全局限流直接放在 `default-filters`，无法表达 API、应用、全局三级覆盖。
4. URL 权限数据已经按 HTTP Method 和 URL 建模，但“是否发布”和“谁能访问”尚未形成明确边界。
5. 当前新增、修改、删除会立即写 Nacos，尚未完整使用已有草稿、版本、发布和实例生效表。

## 3. 目标与非目标

### 3.1 目标

1. 支持按 `HTTP Method + Path` 独立发现、发布、下线和查询 API。
2. 保留 `/服务名/**`、`/**` 等应用级通配路由。
3. 让限流、超时、熔断同时支持全局、应用、API 三个层级。
4. 对每项策略采用 `API > 应用 > 全局` 的独立覆盖规则。
5. 复用 Nacos 作为运行时配置源，复用现有 CAS 发布、审计、权限和配置版本结构。
6. 管理端能够同时展示“本级配置”和“最终生效配置及来源”。
7. 发布前能够发现路由冲突、覆盖关系、无效策略和权限缺口。
8. 为应用级通配路由的特批流程预留字段和状态，但本次不实现审批引擎。

### 3.2 非目标

1. 不替换 Nacos，不建设第二套网关运行时配置中心。
2. 不让浏览器直接读写 Nacos。
3. 不将 `base_org_resource` 复制成另一套角色权限模型。
4. 第一阶段不实现灰度流量、API 计费、开发者门户或外部订阅。
5. 第一阶段不实现应用级通配路由的多人审批流程。

## 4. 核心概念

### 4.1 服务

注册中心中的可路由应用，以 `service_id` 唯一标识，例如
`base-organization`。服务是应用级路由和应用级默认策略的归属对象。

### 4.2 API 资产

从应用 OpenAPI 文档同步或由管理员补录的接口定义。身份键为：

```text
service_id + http_method + upstream_path
```

OpenAPI 的 `operationId` 作为可读标识，不能单独作为稳定主键。

### 4.3 API 发布

API 资产的外部暴露声明，包含外部 Path、目标 Path、鉴权模式、状态和策略覆盖。
API 资产存在不代表已经对外发布。

### 4.4 应用级路由

面向某个服务的通配路由，例如：

```text
/api/organization/**
/base-organization/**
/**
```

应用级路由是受支持能力。`/**` 和 `/服务名/**` 标记为高风险，当前允许具备相应权限的
管理员发布；后续在相同模型上增加特批。

### 4.5 治理策略

第一批治理策略包括：

- 限流 `RATE_LIMIT`
- 超时 `TIMEOUT`
- 熔断 `CIRCUIT_BREAKER`
- IP 黑白名单 `ACCESS_CONTROL`
- 全局默认过滤器 `DEFAULT_FILTERS`（仅全局，兼容旧 `SECURITY_HEADERS` 草稿）
- 跨域规则 `CORS`（仅全局）

限流、超时、熔断和 IP 黑白名单可分别配置在 `GLOBAL`、`APPLICATION`、`API`
作用域；默认过滤器与跨域规则只允许 `GLOBAL`。

## 5. 总体架构

```text
Nacos 服务目录 ─┐
                ├─> 服务与 OpenAPI 发现 ─> API 资产目录
应用 OpenAPI ───┘                              │
                                               ▼
管理员配置 ─────────────────────────────> API/应用发布模型
                                               │
全局策略 ─> 应用策略 ─> API 策略 ───────> Effective Policy Resolver
                                               │
                                               ▼
                                      Route Configuration Compiler
                                               │
                                      校验、差异、版本、审计
                                               │
                                               ▼
                                         Nacos CAS 发布
                                               │
                                               ▼
                                       base-gateway 热加载
```

控制面保存业务语义；Nacos 保存编译后的 Spring Cloud Gateway 运行时配置。

## 6. 路由设计

### 6.1 路由类型

| 类型 | 匹配条件 | 默认优先级区间 | 说明 |
|---|---|---:|---|
| API | Method + 精确/模板 Path | -1000 ～ -1 | 常规独立发布 |
| 应用 | Path 通配，可选 Method | 100 ～ 999 | 应用整体或前缀代理 |
| 兜底 | `/**` | 1000 及以后 | 高风险兜底 |

控制面分配和校验优先级，普通用户不直接填写原始 `order`。

### 6.2 匹配规则

1. API 路由必须包含 Path 和 Method。
2. 应用级路由至少包含 Path，Method 可选。
3. API 路由优先于应用级路由，应用级路由优先于兜底路由。
4. 相同 Method 和等价 Path 不允许存在两个相同优先级的已发布 API。
5. 控制面必须检测路由被更宽通配规则覆盖或遮蔽的情况。
6. API 未独立发布但能通过应用级路由访问时，状态显示为“经应用路由暴露”，不能显示为“未暴露”。

### 6.3 风险等级

| 风险 | 示例 |
|---|---|
| LOW | `GET /api/users/{id}` |
| MEDIUM | `/api/organization/*` |
| HIGH | `/base-organization/**`、`/**`、无 Method 的宽通配 |

发布模型预留：

- `risk_level`
- `approval_status`
- `approval_reason`
- `approved_by`
- `approved_time`

当前 `approval_status` 固定为 `NOT_REQUIRED`；未来 HIGH 路由切换为
`PENDING/APPROVED/REJECTED`。

## 7. 策略继承与覆盖

### 7.1 优先级

每一种策略独立按以下顺序解析：

```text
API > APPLICATION > GLOBAL
```

不能将 API 层配置的某一项策略误解为覆盖全部策略。

### 7.2 三态语义

每个作用域中的每项策略都有三种模式：

| 模式 | 含义 |
|---|---|
| `INHERIT` | 不在本级作决定，继续查找上一级 |
| `ENABLED` | 本级启用，使用本级参数 |
| `DISABLED` | 本级明确关闭，不再继承上级 |

`null` 不能同时表达继承和关闭。数据库与 API 必须显式使用 `mode`。

### 7.3 解析算法

```text
resolve(policyType, apiId, serviceId):
  if API policy mode != INHERIT:
    return API policy
  if APPLICATION policy mode != INHERIT:
    return APPLICATION policy
  return GLOBAL policy
```

最终结果同时返回：

- `effectiveMode`
- `effectiveConfig`
- `sourceScope`
- `sourceId`
- `sourceVersion`

### 7.4 编译原则

分层策略只存在于控制面。发布时先计算 Effective Policy，再为每条最终 Route
生成唯一的一套限流、超时、熔断和 IP 访问控制过滤器。

禁止把三级策略分别写入 `default-filters`、应用路由和 API 路由后依赖
Spring Cloud Gateway 自行覆盖；过滤器会叠加执行，不具备本设计需要的继承语义。

`default-filters` 仅保留真正无条件叠加的横切过滤器，例如 TokenRelay 和统一响应头。

### 7.5 策略参数

#### 限流

- Key 类型：IP、用户、OAuth Client、API
- `replenishRate`
- `burstCapacity`
- 可选 `requestedTokens`

#### 超时

- `connectTimeoutMs`
- `responseTimeoutMs`

连接超时若由底层 HTTP Client 统一控制，应在编译器中明确其全局边界；路由级主要生成
响应超时配置，具体 Spring Cloud Gateway 配置形式在实现 Spike 中确认。

#### 熔断

- `failureRateThreshold`
- `slowCallRateThreshold`
- `slowCallDurationThreshold`
- `minimumNumberOfCalls`
- `waitDurationInOpenState`
- 可选 `fallbackUri`

熔断器实例名必须由稳定路由 ID 派生，避免不同 API 意外共享状态。

#### IP 黑白名单

- 模式：`ALLOWLIST`（仅命中放行）或 `DENYLIST`（命中拒绝）
- 条目：IPv4、IPv6 或 CIDR，可附带说明
- 每项策略最多 20 条；启用时至少 1 条

网关只在直接连接来源属于 `opensabre.gateway.client-ip.trusted-proxies` 时读取
`X-Forwarded-For`，避免客户端伪造来源地址。白名单发布前必须加入实际管理入口与运维出口。

## 8. 鉴权边界

发布与授权是两个不同问题：

| 发布状态/鉴权模式 | 行为 |
|---|---|
| 未被任何路由暴露 | 404 |
| 经 API 或应用路由暴露 + PUBLIC | 无需登录 |
| 经 API 或应用路由暴露 + AUTHENTICATED | 登录用户可访问 |
| 经 API 或应用路由暴露 + RESOURCE_REQUIRED | 按 Method + URL 检查组织资源权限 |

`RESOURCE_REQUIRED` 通过 `resource_id` 关联现有 `base_org_resource`。不在网关控制面复制
角色、用户和角色资源关系。

应用级路由可能覆盖多个资源，因此授权仍在请求时按照实际 Method + URL 匹配，不能只按
应用路由 ID 授权。

## 9. 数据模型

### 9.1 API 资产

`base_gateway_admin_api`

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `service_id` | 注册中心服务 ID |
| `operation_id` | OpenAPI operationId |
| `http_method` | HTTP 方法 |
| `upstream_path` | 应用真实路径 |
| `summary`、`tags_json` | 展示元数据 |
| `source_type` | OPENAPI/MANUAL |
| `source_hash` | 接口定义摘要 |
| `discovery_status` | ACTIVE/MISSING |
| `last_discovered_time` | 最近同步时间 |

唯一键：`service_id + http_method + upstream_path`。

### 9.2 API 发布

`base_gateway_admin_api_publication`

| 字段 | 说明 |
|---|---|
| `api_id` | API 资产 |
| `external_path` | 外部 Path |
| `upstream_path` | 可选覆盖目标 Path |
| `auth_mode` | PUBLIC/AUTHENTICATED/RESOURCE_REQUIRED |
| `resource_id` | 组织资源 ID |
| `status` | DRAFT/PUBLISHED/OFFLINE |
| `risk_level` | LOW/MEDIUM/HIGH |
| `approval_*` | 后续审批预留 |
| `published_version` | 最近生效配置版本 |
| `lock_version` | 乐观锁 |

### 9.3 应用级路由

`base_gateway_admin_application_route`

保存 `service_id`、外部 Path、目标 URI、可选 Method、路径改写、状态、风险和审批预留字段。

### 9.4 分层策略

`base_gateway_admin_policy`

| 字段 | 说明 |
|---|---|
| `scope_type` | GLOBAL/APPLICATION/API |
| `scope_id` | 全局固定值、service_id 或 api_id |
| `policy_type` | RATE_LIMIT/TIMEOUT/CIRCUIT_BREAKER/ACCESS_CONTROL/DEFAULT_FILTERS/CORS |
| `mode` | INHERIT/ENABLED/DISABLED |
| `config_json` | 类型化配置的持久化形式 |
| `lock_version` | 乐观锁 |

唯一键：`scope_type + scope_id + policy_type`。

服务层必须把 `config_json` 转换为按类型校验的 DTO，Controller 不接收任意 JSON。

安全响应头和 CORS 仍复用该表，不新增平行配置表。`config_json` 使用 `TEXT`，以容纳
CSP、来源列表和自定义 Header 等类型化配置。

### 9.5 复用发布表

继续使用：

- `base_gateway_admin_config_draft`
- `base_gateway_admin_config_version`
- `base_gateway_admin_release`
- `base_gateway_admin_instance_revision`

发布记录增加变更摘要或关联表，用于追踪一次发布影响的 API、应用路由和策略。

## 10. API 资产同步

1. 从现有服务目录选择健康实例。
2. 通过服务内部地址读取 OpenAPI，不经过外部网关入口。
3. 解析 Method、Path、operationId、summary 和 tags。
4. 使用稳定身份键执行幂等 upsert。
5. 新发现接口状态为 ACTIVE，但不自动发布。
6. OpenAPI 中消失的接口标记为 MISSING，不物理删除。
7. 已发布 API 变为 MISSING 时产生告警，不能静默下线。
8. 同步失败不修改上一份有效资产快照。

## 11. 发布流程

```text
保存草稿
  → 解析三级有效策略
  → 路由冲突与权限校验
  → 生成完整运行时配置
  → 展示语义差异和 YAML 差异
  → 创建不可变配置版本
  → Nacos CAS 发布
  → 记录发布结果
  → 验证网关实例加载
  → 执行路由探测
  → 完成或回滚
```

发布是全量配置原子替换，但管理端操作和审计粒度保持在 API、应用路由和策略。

### 11.1 发布前校验

- 外部 Method + Path 冲突。
- Route ID 冲突。
- 路由优先级反转或不可达。
- Path Rewrite 无法映射到目标 Path。
- `RESOURCE_REQUIRED` 未关联有效资源。
- 策略参数越界或缺少必填项。
- 熔断 fallback 形成循环。
- API 资产处于 MISSING。
- 当前 Nacos MD5 与草稿基线不一致。
- 自定义 Header 包含受保护名称或换行注入字符。
- CORS 来源、凭证通配、方法或缓存时间不合法。

`DEFAULT_FILTERS` 草稿按顺序写入 `spring.cloud.gateway.default-filters`，且必须保留启用的
唯一 `TokenRelay`；CORS 写入 `spring.cloud.gateway.globalcors`。没有草稿时不接管既有
节点，管理端首次编辑时从当前运行配置完整导入。安全响应头作为过滤器快捷模板生成。

### 11.2 回滚

回滚不是重新保存旧草稿，而是以历史不可变版本为输入创建一次新的发布记录，并使用
Nacos CAS 发布。回滚同样执行校验、实例确认和探测。

## 12. 管理端信息架构

```text
网关管理
├── 服务与 API
│   ├── 服务列表
│   ├── API 资产
│   └── API 发布详情
├── 应用路由
├── 治理策略
│   ├── 全局
│   ├── 应用
│   └── API
├── 全局规则
│   ├── 默认过滤器（含安全响应头快捷模板）
│   └── CORS
└── 发布记录
```

### 12.1 API 列表

展示 Method、上游 Path、外部 Path、服务、独立发布状态、实际暴露状态、鉴权模式和有效策略。

实际暴露状态包括：

- 独立发布
- 经应用路由暴露
- 未暴露
- 已下线但仍被通配路由覆盖
- 路由冲突

### 12.2 策略编辑

每个策略同时展示：

- 本级模式与配置
- 上级配置
- 最终生效值
- 生效来源

修改 API 策略时应实时预览 Effective Policy，但最终结果以服务端解析为准。

### 12.3 发布确认

发布确认必须展示：

- 基线版本和目标版本
- 新增、修改、下线的 API/应用路由
- 策略有效值变化
- 新增的通配暴露范围
- 权限缺口和风险等级

## 13. API 草案

```text
GET    /services
POST   /services/{serviceId}/apis/sync
GET    /apis
GET    /apis/{apiId}
PUT    /apis/{apiId}/publication
POST   /apis/{apiId}/publish
POST   /apis/{apiId}/offline

GET    /application-routes
POST   /application-routes
PUT    /application-routes/{id}
POST   /application-routes/{id}/publish
POST   /application-routes/{id}/offline

GET    /policies/effective
GET    /policies/{scopeType}/{scopeId}
PUT    /policies/{scopeType}/{scopeId}/{policyType}

POST   /releases/validate
POST   /releases
GET    /releases
GET    /releases/{id}
POST   /releases/{id}/rollback
```

实际路径继续使用现有 `base-gateway-admin` 网关前缀，以上只描述控制器资源结构。

## 14. 可观测性

至少记录：

- API 资产同步成功、失败、耗时和变更数量。
- 发布成功、失败、CAS 冲突和回滚。
- 实例加载版本及不一致实例数。
- 每条路由的请求量、状态码、延迟。
- 限流拒绝数、超时数、熔断状态和 fallback 次数。
- 路由 ID、API ID、service_id 和 release_id 的关联标签。

禁止把完整 Token、Secret 或敏感请求参数写入日志和指标标签。

## 15. 兼容与迁移

1. 首先把当前 Nacos 显式路由导入为“托管路由”。
2. 能映射到单个 Method + Path 的导入为 API 发布。
3. 通配 Path 导入为应用级路由，不强制拆分。
4. 无法识别的 Predicate/Filter 进入只读兼容模式，禁止表单覆盖丢失。
5. 新旧控制面迁移期间只允许一个写入口。
6. 服务发现自动路由的处置作为独立切换项；关闭前先盘点实际流量，不能直接假设无调用。

## 16. 关键决策

1. API 资产、发布和授权分离，避免生命周期耦合。
2. 应用级通配路由是一等能力，不是非法配置。
3. 特批字段现在预留，审批流程后续实现。
4. 策略按单项三级继承，明确支持本级关闭。
5. 控制面解析 Effective Policy，运行时不叠加三级治理过滤器。
6. Nacos 继续保存完整编译结果，数据库保存业务语义和不可变发布历史。

## 17. 开放问题

1. 路由级超时最终采用 Spring Cloud Gateway metadata、过滤器还是自定义 GatewayFilter；
   在第一迭代的技术 Spike 中验证。
2. 熔断器具体采用 Spring Cloud CircuitBreaker/Resilience4j 的参数映射和依赖版本，需要
   在 Spike 中确认。
3. API OpenAPI 内部访问地址是否对所有服务统一，需要服务目录能力核验。
4. 第一批 Key Resolver 是否只支持 IP 和请求路径，还是同步加入用户与 OAuth Client。
5. 关闭 discovery locator 的目标环境和迁移窗口需要通过真实流量盘点决定。

## 18. 验收原则

- API 路由优先于应用路由，应用路由优先于兜底路由。
- API、应用、全局任意组合下，每项策略的有效值和来源可预测、可测试。
- 一次请求不会因三级配置而重复执行同类型治理策略。
- “未独立发布但经应用路由暴露”的 API 能被准确识别。
- 发布、冲突、实例生效和回滚均可追踪到不可变配置版本。
- 现有无法结构化识别的路由不会在迁移中被静默丢失。
