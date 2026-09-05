
ALTER TABLE base_gateway_admin_policy
    MODIFY COLUMN config_json TEXT NULL COMMENT '类型化策略 JSON 配置';
