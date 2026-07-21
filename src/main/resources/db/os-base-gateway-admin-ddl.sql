CREATE DATABASE IF NOT EXISTS os_base_gateway_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE os_base_gateway_admin;

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
    UNIQUE KEY uk_gateway_config_version (version)
) COMMENT='不可变网关配置版本';

CREATE TABLE IF NOT EXISTS base_gateway_admin_release (
    id BIGINT NOT NULL PRIMARY KEY,
    draft_id BIGINT,
    target_version VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000),
    started_time DATETIME NOT NULL,
    completed_time DATETIME,
    created_by VARCHAR(100) NOT NULL,
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
