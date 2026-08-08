ALTER TABLE base_gateway_admin_api_publication
    ADD COLUMN IF NOT EXISTS filters_json TEXT AFTER upstream_path;
