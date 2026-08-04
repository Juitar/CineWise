package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 按 V008 的 request_hash v1 规则生成可重复的请求摘要。 */
@Component
public final class AgentRequestHashFactory {
    private static final String VERSION_V1 = "v1";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 对内容和服务器已校验的槽位快照计算 UTF-8 SHA-256。
     *
     * <p>runId、traceId、超时预算和工具白名单不属于输入，调用方不得将它们混入槽位快照。
     */
    public AgentRequestHash create(String content, SlotSnapshot slotSnapshot) {
        String canonicalJson = canonicalize(content, slotSnapshot);
        return new AgentRequestHash(VERSION_V1, sha256Hex(canonicalJson));
    }

    /** 返回固定字段顺序的规范化 JSON，供单元测试和重复请求诊断使用。 */
    public String canonicalize(String content, SlotSnapshot slotSnapshot) {
        Objects.requireNonNull(content, "消息内容不能为空");
        Objects.requireNonNull(slotSnapshot, "槽位快照不能为空");
        if (slotSnapshot.version() < 0) {
            throw new IllegalArgumentException("槽位快照版本不能为负数");
        }
        String normalizedContent = normalize(content);
        List<SlotEntry> slots = normalizeSlots(slotSnapshot.values());
        StringBuilder json = new StringBuilder();
        json.append("{\"v\":\"v1\",\"content\":");
        appendJsonString(json, normalizedContent);
        json.append(",\"slotSnapshotVersion\":").append(slotSnapshot.version()).append(",\"slots\":{");
        for (int index = 0; index < slots.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            SlotEntry slot = slots.get(index);
            appendJsonString(json, slot.key());
            json.append(':');
            appendJsonString(json, slot.value());
        }
        return json.append("}}").toString();
    }

    private static List<SlotEntry> normalizeSlots(Map<String, String> values) {
        Objects.requireNonNull(values, "槽位值不能为空");
        List<SlotEntry> entries = new ArrayList<>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            entries.add(new SlotEntry(normalize(entry.getKey()), normalize(entry.getValue())));
        }
        entries.sort((left, right) -> compareByCodePoint(left.key(), right.key()));
        for (int index = 1; index < entries.size(); index++) {
            if (entries.get(index - 1).key().equals(entries.get(index).key())) {
                throw new IllegalArgumentException("规范化后存在重复槽位键");
            }
        }
        return entries;
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "规范化字符串不能为空");
        return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
    }

    private static int compareByCodePoint(String left, String right) {
        int leftOffset = 0;
        int rightOffset = 0;
        while (leftOffset < left.length() && rightOffset < right.length()) {
            int leftCodePoint = left.codePointAt(leftOffset);
            int rightCodePoint = right.codePointAt(rightOffset);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftOffset += Character.charCount(leftCodePoint);
            rightOffset += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftOffset, right.length() - rightOffset);
    }

    private static void appendJsonString(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                default -> appendJsonCharacter(target, character);
            }
        }
        target.append('"');
    }

    private static void appendJsonCharacter(StringBuilder target, char character) {
        if (character < 0x20) {
            target.append("\\u");
            target.append(HEX[(character >>> 12) & 0x0f]);
            target.append(HEX[(character >>> 8) & 0x0f]);
            target.append(HEX[(character >>> 4) & 0x0f]);
            target.append(HEX[character & 0x0f]);
            return;
        }
        if (Character.isSurrogate(character)) {
            throw new IllegalArgumentException("请求摘要输入不能包含未配对的 UTF-16 代理字符");
        }
        target.append(character);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                int unsignedValue = Byte.toUnsignedInt(valueByte);
                hex.append(HEX[unsignedValue >>> 4]).append(HEX[unsignedValue & 0x0f]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }

    private record SlotEntry(String key, String value) {
    }
}
