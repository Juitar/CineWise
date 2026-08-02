-- OpenSpec: content-and-show-selection-flow
-- Cross-module identifiers are logical references; the modular monolith does not use physical foreign keys.
-- Owners confirmed: keep FLYWAY_ENABLED=false until A starts the controlled MySQL 8.4 validation.

CREATE TABLE `auditorium` (
    `id` BIGINT NOT NULL,
    `cinema_id` BIGINT NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `hall_type` VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
    `row_count` INT NOT NULL,
    `seat_count` INT NOT NULL,
    `data_type` VARCHAR(16) NOT NULL DEFAULT 'MOCK',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auditorium_cinema_name` (`cinema_id`, `name`),
    CONSTRAINT `chk_auditorium_row_count`
        CHECK (`row_count` BETWEEN 1 AND 50),
    CONSTRAINT `chk_auditorium_seat_count`
        CHECK (`seat_count` BETWEEN 1 AND 500)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `movie_show` (
    `id` BIGINT NOT NULL,
    `movie_id` BIGINT NOT NULL,
    `cinema_id` BIGINT NOT NULL,
    `auditorium_id` BIGINT NOT NULL,
    `start_time` DATETIME(3) NOT NULL,
    `end_time` DATETIME(3) NOT NULL,
    `language_version` VARCHAR(32) NOT NULL,
    `base_price` DECIMAL(10, 2) NOT NULL,
    `data_type` VARCHAR(16) NOT NULL DEFAULT 'MOCK',
    `source` VARCHAR(32) NOT NULL DEFAULT 'demo-seed',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ON_SALE',
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_show_auditorium_start` (`auditorium_id`, `start_time`),
    KEY `idx_show_movie_time` (`movie_id`, `start_time`),
    KEY `idx_show_cinema_time` (`cinema_id`, `start_time`),
    KEY `idx_show_status_time` (`status`, `start_time`),
    CONSTRAINT `chk_show_time`
        CHECK (`end_time` > `start_time`),
    CONSTRAINT `chk_show_price`
        CHECK (`base_price` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `show_seat` (
    `id` BIGINT NOT NULL,
    `show_id` BIGINT NOT NULL,
    `row_no` VARCHAR(8) NOT NULL,
    `seat_no` VARCHAR(8) NOT NULL,
    `seat_label` VARCHAR(24) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    `lock_order_no` VARCHAR(32) NULL,
    `lock_expire_time` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME(3) NOT NULL,
    `update_time` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_show_seat` (`show_id`, `row_no`, `seat_no`),
    KEY `idx_show_seat_status` (`show_id`, `status`),
    KEY `idx_seat_lock_expire` (`status`, `lock_expire_time`),
    KEY `idx_seat_lock_order` (`lock_order_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
