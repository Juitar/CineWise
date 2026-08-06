-- 草案，仅供 A 静态复核。
-- OpenSpec: travel-reminder-experience
-- 禁止直接放入 backend/src/main/resources/db/migration/，也不得执行。
--
-- V007 已发布，历史任务保留 cinema_id 为 NULL；
-- 新支付任务的非空校验由应用层事件消费者负责；
-- 退款先到且影院 ID 非法时仍创建 CANCELLED 墓碑并保留 NULL。
-- 本约束保留历史 NULL，同时拒绝 0 和负数。
-- 本迁移不建立物理外键、不设置默认值、不回填历史数据。

ALTER TABLE `travel_task`
    ADD COLUMN `cinema_id` BIGINT NULL AFTER `show_id`,
    ADD CONSTRAINT `chk_travel_task_cinema_id_positive`
        CHECK (`cinema_id` IS NULL OR `cinema_id` > 0);
