ALTER TABLE base_gateway_admin_api
    ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT 'COMMON' AFTER service_id;

CREATE INDEX idx_gateway_api_product ON base_gateway_admin_api (product_code);
