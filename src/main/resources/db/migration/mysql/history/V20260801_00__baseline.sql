
CREATE TABLE IF NOT EXISTS base_gateway_admin_config_draft (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_version VARCHAR(64) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    INDEX idx_gateway_draft_status (status)
) COMMENT='网关配置草稿';

CREATE TABLE IF NOT EXISTS base_gateway_admin_config_version (
    id BIGINT NOT NULL PRIMARY KEY,
    version VARCHAR(64) NOT NULL,
    content LONGTEXT NOT NULL,
    source_version VARCHAR(64),
    created_time DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_config_version (version)
) COMMENT='不可变网关配置版本';

CREATE TABLE IF NOT EXISTS base_gateway_admin_release (
    id BIGINT NOT NULL PRIMARY KEY,
    draft_id BIGINT,
    target_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(1000),
    started_time DATETIME NOT NULL,
    completed_time DATETIME,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    INDEX idx_gateway_release_status (status),
    INDEX idx_gateway_release_version (target_version)
) COMMENT='网关配置发布记录';

CREATE TABLE IF NOT EXISTS base_gateway_admin_instance_revision (
    id BIGINT NOT NULL PRIMARY KEY,
    release_id BIGINT,
    instance_id VARCHAR(200) NOT NULL,
    loaded_version VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    reported_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_release_instance (release_id, instance_id),
    INDEX idx_gateway_instance_status (status)
) COMMENT='网关实例配置生效状态';

CREATE TABLE IF NOT EXISTS base_gateway_admin_api (
    id BIGINT NOT NULL PRIMARY KEY,
    service_id VARCHAR(100) NOT NULL,
    operation_id VARCHAR(200),
    http_method VARCHAR(16) NOT NULL,
    upstream_path VARCHAR(500) NOT NULL,
    summary VARCHAR(500),
    tags_json VARCHAR(2000),
    source_type VARCHAR(20) NOT NULL DEFAULT 'OPENAPI',
    source_hash VARCHAR(64),
    discovery_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_discovered_time DATETIME,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_api_identity (service_id, http_method, upstream_path),
    INDEX idx_gateway_api_service_status (service_id, discovery_status)
) COMMENT='网关 API 资产';

CREATE TABLE IF NOT EXISTS base_gateway_admin_api_publication (
    id BIGINT NOT NULL PRIMARY KEY,
    api_id BIGINT NOT NULL,
    external_path VARCHAR(500) NOT NULL,
    upstream_path VARCHAR(500),
    auth_mode VARCHAR(30) NOT NULL,
    resource_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    approval_reason VARCHAR(1000),
    approved_by VARCHAR(100),
    approved_time DATETIME,
    published_version VARCHAR(64),
    lock_version INT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_api_publication (api_id),
    INDEX idx_gateway_api_publication_status (status)
) COMMENT='网关 API 发布声明';

CREATE TABLE IF NOT EXISTS base_gateway_admin_application_route (
    id BIGINT NOT NULL PRIMARY KEY,
    service_id VARCHAR(100) NOT NULL,
    route_name VARCHAR(100) NOT NULL,
    external_path VARCHAR(500) NOT NULL,
    target_uri VARCHAR(500) NOT NULL,
    http_method VARCHAR(16),
    rewrite_path VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    risk_level VARCHAR(20) NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    approval_reason VARCHAR(1000),
    approved_by VARCHAR(100),
    approved_time DATETIME,
    published_version VARCHAR(64),
    lock_version INT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_application_route (service_id, external_path, http_method),
    INDEX idx_gateway_application_route_status (status)
) COMMENT='网关应用级路由';

CREATE TABLE IF NOT EXISTS base_gateway_admin_policy (
    id BIGINT NOT NULL PRIMARY KEY,
    scope_type VARCHAR(20) NOT NULL,
    scope_id VARCHAR(100) NOT NULL,
    policy_type VARCHAR(30) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    config_json VARCHAR(4000),
    lock_version INT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_policy_scope (scope_type, scope_id, policy_type)
) COMMENT='网关分层治理策略';

CREATE TABLE IF NOT EXISTS base_gateway_admin_release_item (
    id BIGINT NOT NULL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    summary VARCHAR(1000),
    INDEX idx_gateway_release_item (release_id, item_type)
) COMMENT='网关发布影响项';

CREATE TABLE IF NOT EXISTS base_gateway_admin_route_probe (
    id BIGINT NOT NULL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    instance_id VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    missing_route_ids_json LONGTEXT,
    error_message VARCHAR(1000),
    probed_time DATETIME NOT NULL,
    UNIQUE KEY uk_gateway_release_route_probe (release_id, instance_id),
    INDEX idx_gateway_route_probe_status (release_id, status)
) COMMENT='网关发布后路由装载探测';

-- 兼容已初始化的 0.7.0/早期 0.7.1 数据库：发布实体继承 BasePo，必须具备完整审计字段。
SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'base_gateway_admin_config_version'
                        AND COLUMN_NAME = 'updated_by');
SET @ddl = IF(@column_exists = 0,
              'ALTER TABLE base_gateway_admin_config_version ADD COLUMN updated_by VARCHAR(100) NOT NULL DEFAULT ''system''',
              'SELECT 1');
PREPARE gateway_admin_stmt FROM @ddl;
EXECUTE gateway_admin_stmt;
DEALLOCATE PREPARE gateway_admin_stmt;

ALTER TABLE base_gateway_admin_release
    MODIFY COLUMN status VARCHAR(32) NOT NULL;

SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'base_gateway_admin_config_version'
                        AND COLUMN_NAME = 'updated_time');
SET @ddl = IF(@column_exists = 0,
              'ALTER TABLE base_gateway_admin_config_version ADD COLUMN updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
              'SELECT 1');
PREPARE gateway_admin_stmt FROM @ddl;
EXECUTE gateway_admin_stmt;
DEALLOCATE PREPARE gateway_admin_stmt;

SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'base_gateway_admin_release'
                        AND COLUMN_NAME = 'created_time');
SET @ddl = IF(@column_exists = 0,
              'ALTER TABLE base_gateway_admin_release ADD COLUMN created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
              'SELECT 1');
PREPARE gateway_admin_stmt FROM @ddl;
EXECUTE gateway_admin_stmt;
DEALLOCATE PREPARE gateway_admin_stmt;

SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'base_gateway_admin_release'
                        AND COLUMN_NAME = 'updated_by');
SET @ddl = IF(@column_exists = 0,
              'ALTER TABLE base_gateway_admin_release ADD COLUMN updated_by VARCHAR(100) NOT NULL DEFAULT ''system''',
              'SELECT 1');
PREPARE gateway_admin_stmt FROM @ddl;
EXECUTE gateway_admin_stmt;
DEALLOCATE PREPARE gateway_admin_stmt;

SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'base_gateway_admin_release'
                        AND COLUMN_NAME = 'updated_time');
SET @ddl = IF(@column_exists = 0,
              'ALTER TABLE base_gateway_admin_release ADD COLUMN updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
              'SELECT 1');
PREPARE gateway_admin_stmt FROM @ddl;
EXECUTE gateway_admin_stmt;
DEALLOCATE PREPARE gateway_admin_stmt;
