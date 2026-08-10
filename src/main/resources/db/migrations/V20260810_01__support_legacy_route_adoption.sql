USE os_base_gateway_admin;
SET NAMES utf8mb4;

ALTER TABLE base_gateway_admin_application_route
    ADD COLUMN legacy_route_id VARCHAR(200) NULL AFTER published_version;

CREATE UNIQUE INDEX uk_gateway_application_legacy_route
    ON base_gateway_admin_application_route (legacy_route_id);
