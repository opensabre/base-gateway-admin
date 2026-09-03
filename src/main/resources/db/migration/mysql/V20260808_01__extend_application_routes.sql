SET NAMES utf8mb4;

ALTER TABLE base_gateway_admin_application_route
    ADD COLUMN route_order INT NOT NULL DEFAULT 100 AFTER rewrite_path,
    ADD COLUMN predicates_json TEXT AFTER route_order,
    ADD COLUMN filters_json TEXT AFTER predicates_json;
