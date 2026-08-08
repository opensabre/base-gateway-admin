ALTER TABLE base_gateway_admin_api_publication
    ADD COLUMN filters_json TEXT AFTER upstream_path;
