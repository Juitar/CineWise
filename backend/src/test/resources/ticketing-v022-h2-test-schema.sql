-- H2快速测试仍固定在V009；只补齐当前支付查询必需的V022列，不模拟MySQL索引或生命周期SQL。
ALTER TABLE electronic_ticket ADD COLUMN IF NOT EXISTS invalidation_reason VARCHAR(32);
