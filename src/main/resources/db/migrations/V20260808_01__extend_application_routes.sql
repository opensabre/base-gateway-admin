USE os_base_gateway_admin;
SET NAMES utf8mb4;

ALTER TABLE base_gateway_admin_application_route
    ADD COLUMN IF NOT EXISTS route_order INT NOT NULL DEFAULT 100 AFTER rewrite_path,
    ADD COLUMN IF NOT EXISTS predicates_json TEXT AFTER route_order,
    ADD COLUMN IF NOT EXISTS filters_json TEXT AFTER predicates_json;
