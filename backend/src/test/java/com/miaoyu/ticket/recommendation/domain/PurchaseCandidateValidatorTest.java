package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PurchaseCandidateValidatorTest {

    /*
     * 可购候选校验是防止动态票务事实被错误展示的最后一道 D 侧规则。
     * 测试只校验格式和时效，不替代 A 的库存或建单校验。
     * 固定时间用于覆盖 expiresAt 的临界条件。
     * 金额必须为两位小数字符串，不能接受前端 Number。
     * 任一字段不可信时应排除候选，而不是尝试修复。
     */

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void shouldAcceptOnlyCompleteFreshCandidateWithTwoDecimalPrice() {
        PurchaseCandidateValidator.PurchaseCandidate candidate = new PurchaseCandidateValidator.PurchaseCandidate(
                "301", "101", "201", "45.00", NOW.plusSeconds(3_600), NOW.plusSeconds(1_800), "TICKETING");

        // 所有 A 提供的字段完整且未过期时，D 才允许候选进入可购结果。
        assertThat(PurchaseCandidateValidator.isEligible(candidate, NOW)).isTrue();
    }

    @Test
    void shouldRejectIncompleteInvalidPriceOrExpiredCandidate() {
        PurchaseCandidateValidator.PurchaseCandidate invalidPrice = new PurchaseCandidateValidator.PurchaseCandidate(
                "301", "101", "201", "45", NOW.plusSeconds(3_600), NOW.plusSeconds(1_800), "TICKETING");
        PurchaseCandidateValidator.PurchaseCandidate expired = new PurchaseCandidateValidator.PurchaseCandidate(
                "301", "101", "201", "45.00", NOW.plusSeconds(3_600), NOW, "TICKETING");
        // 过期或价格格式错误时宁可没有可购结果，也不能把不可靠票务事实交给 Agent。
        // 一位小数不能作为 API 金额。
        assertThat(PurchaseCandidateValidator.isEligible(invalidPrice, NOW)).isFalse();
        // expiresAt 等于当前时刻也必须排除。
        assertThat(PurchaseCandidateValidator.isEligible(expired, NOW)).isFalse();
    }
}
