-- Generated from the complete verified migration history.
-- Regenerate with base-k8s/scripts/generate-flyway-baselines.sh; do not edit manually.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `base_gateway_admin_api`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_api` (
  `id` bigint NOT NULL,
  `service_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON',
  `operation_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `http_method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `upstream_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags_json` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPENAPI',
  `source_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discovery_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `last_discovered_time` datetime DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_api_identity` (`service_id`,`http_method`,`upstream_path`),
  KEY `idx_gateway_api_service_status` (`service_id`,`discovery_status`),
  KEY `idx_gateway_api_product` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关 API 资产';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_api_publication`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_api_publication` (
  `id` bigint NOT NULL,
  `api_id` bigint NOT NULL,
  `external_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `upstream_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `filters_json` text COLLATE utf8mb4_unicode_ci,
  `auth_mode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOW',
  `approval_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_REQUIRED',
  `approval_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_time` datetime DEFAULT NULL,
  `published_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lock_version` int NOT NULL DEFAULT '0',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_api_publication` (`api_id`),
  KEY `idx_gateway_api_publication_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关 API 发布声明';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_application_route`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_application_route` (
  `id` bigint NOT NULL,
  `service_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_uri` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `http_method` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rewrite_path` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `route_order` int NOT NULL DEFAULT '100',
  `predicates_json` text COLLATE utf8mb4_unicode_ci,
  `filters_json` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `approval_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_REQUIRED',
  `approval_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_time` datetime DEFAULT NULL,
  `published_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `legacy_route_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lock_version` int NOT NULL DEFAULT '0',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_application_route` (`service_id`,`external_path`,`http_method`),
  UNIQUE KEY `uk_gateway_application_legacy_route` (`legacy_route_id`),
  KEY `idx_gateway_application_route_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关应用级路由';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_config_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_config_draft` (
  `id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `created_time` datetime NOT NULL,
  `updated_time` datetime NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gateway_draft_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关配置草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_config_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_config_version` (
  `id` bigint NOT NULL,
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_time` datetime NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_config_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变网关配置版本';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_instance_revision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_instance_revision` (
  `id` bigint NOT NULL,
  `release_id` bigint DEFAULT NULL,
  `instance_id` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `loaded_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reported_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_release_instance` (`release_id`,`instance_id`),
  KEY `idx_gateway_instance_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关实例配置生效状态';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_policy` (
  `id` bigint NOT NULL,
  `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` text COLLATE utf8mb4_unicode_ci COMMENT '类型化策略 JSON 配置',
  `lock_version` int NOT NULL DEFAULT '0',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_policy_scope` (`scope_type`,`scope_id`,`policy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关分层治理策略';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_release`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_release` (
  `id` bigint NOT NULL,
  `draft_id` bigint DEFAULT NULL,
  `target_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_time` datetime NOT NULL,
  `completed_time` datetime DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_time` datetime NOT NULL,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gateway_release_status` (`status`),
  KEY `idx_gateway_release_version` (`target_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关配置发布记录';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_release_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_release_item` (
  `id` bigint NOT NULL,
  `release_id` bigint NOT NULL,
  `item_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `change_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gateway_release_item` (`release_id`,`item_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关发布影响项';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `base_gateway_admin_route_probe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_gateway_admin_route_probe` (
  `id` bigint NOT NULL,
  `release_id` bigint NOT NULL,
  `instance_id` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `missing_route_ids_json` longtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `probed_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_release_route_probe` (`release_id`,`instance_id`),
  KEY `idx_gateway_route_probe_status` (`release_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关发布后路由装载探测';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
