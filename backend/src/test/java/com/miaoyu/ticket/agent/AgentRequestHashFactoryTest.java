package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.miaoyu.ticket.agent.application.persistence.AgentRequestHashFactory;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** request_hash v1 的字段顺序、Unicode 和换行规范化测试。 */
class AgentRequestHashFactoryTest {

    private final AgentRequestHashFactory factory = new AgentRequestHashFactory();

    @Test
    void shouldCanonicalizeInFixedOrderWithNfcAndNormalizedLineBreaks() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("影院", "朝阳");
        slots.put("日期", "2026-08-04");

        String canonical = factory.canonicalize("cafe\u0301\r\n保留空格 ", new SlotSnapshot(3L, slots));

        String expected = "{\"v\":\"v1\",\"content\":\"café\\n保留空格 \","
                + "\"slotSnapshotVersion\":3,\"slots\":{\"影院\":\"朝阳\",\"日期\":\"2026-08-04\"}}";
        assertEquals(expected, canonical);
        assertEquals(factory.create("café\n保留空格 ", new SlotSnapshot(3L, slots)).value(),
                factory.create("cafe\u0301\r\n保留空格 ", new SlotSnapshot(3L, slots)).value());
    }

    @Test
    void shouldRejectDuplicateKeysCreatedByNfcNormalization() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("café", "first");
        slots.put("cafe\u0301", "second");

        assertThrows(IllegalArgumentException.class, () -> factory.create("内容", new SlotSnapshot(1L, slots)));
    }

    @Test
    void shouldAcceptEmojiButRejectAnUnpairedSurrogate() {
        String canonical = factory.canonicalize("推荐😀电影", new SlotSnapshot(1L, Map.of("偏好", "🎬")));

        String expected = "{\"v\":\"v1\",\"content\":\"推荐😀电影\",\"slotSnapshotVersion\":1,"
                + "\"slots\":{\"偏好\":\"🎬\"}}";
        assertEquals(expected, canonical);
        assertThrows(IllegalArgumentException.class,
                () -> factory.create("错误\uD83D", new SlotSnapshot(1L, Map.of())));
    }
}
