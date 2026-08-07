-- OpenSpec: electronic-ticket-show-end-invalidation
-- 向前兼容：为电子票失效原因和已结束场次的有界扫描增加字段与索引，不改写历史状态。

ALTER TABLE `electronic_ticket`
    ADD COLUMN `invalidation_reason` VARCHAR(32) NULL AFTER `invalidated_time`,
    ADD KEY `idx_ticket_status_order_id` (`status`, `order_id`, `id`),
    ADD CONSTRAINT `chk_ticket_invalidation_reason`
        CHECK (`invalidation_reason` IS NULL OR (
            `status` = 'INVALIDATED'
            AND `invalidation_reason` IN ('SHOW_ENDED', 'ADMIN_INVALIDATED')
        ));

ALTER TABLE `movie_show`
    ADD KEY `idx_show_end_time_id` (`end_time`, `id`);
