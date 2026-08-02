-- OpenSpec: content-and-show-selection-flow
-- D owns the content model; A coordinates the global Flyway order for today's baseline.
-- Owners confirmed: keep FLYWAY_ENABLED=false until A starts the controlled MySQL 8.4 validation.

CREATE TABLE `movie` (
    `id` BIGINT NOT NULL,
    `source_movie_id` VARCHAR(128) NULL,
    `title` VARCHAR(255) NOT NULL,
    `genres_json` JSON NULL,
    `duration_minutes` INT NULL,
    `rating` DECIMAL(3, 1) NULL,
    `source_type` VARCHAR(16) NOT NULL,
    `source` VARCHAR(64) NOT NULL,
    `data_time` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted_at` DATETIME(3) NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_movie_source` (`source`, `source_movie_id`),
    KEY `idx_movie_title` (`title`),
    KEY `idx_movie_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `cinema` (
    `id` BIGINT NOT NULL,
    `source_cinema_id` VARCHAR(128) NULL,
    `name` VARCHAR(255) NOT NULL,
    `city_code` VARCHAR(32) NULL,
    `area` VARCHAR(128) NULL,
    `address` VARCHAR(255) NULL,
    `longitude` DECIMAL(10, 7) NULL,
    `latitude` DECIMAL(10, 7) NULL,
    `source_type` VARCHAR(16) NOT NULL,
    `source` VARCHAR(64) NOT NULL,
    `data_time` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted_at` DATETIME(3) NULL,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cinema_source` (`source`, `source_cinema_id`),
    KEY `idx_cinema_city_area` (`city_code`, `area`),
    KEY `idx_cinema_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
