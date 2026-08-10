package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.AgentCityCodeResolver;
import com.miaoyu.ticket.agent.application.model.AgentConversationContext;
import com.miaoyu.ticket.agent.application.model.AgentIntent;
import com.miaoyu.ticket.agent.application.tool.CinemaTitleResolutionTool;
import com.miaoyu.ticket.agent.application.tool.MovieGenreResolutionTool;
import com.miaoyu.ticket.agent.application.tool.MovieTitleResolutionTool;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** 将上一张服务端 QUESTION 的单项回答写入本人会话，模型原文不能直接成为槽位。 */
@Service
public class AgentConversationSlotService {
    private static final Pattern CITY_CODE = Pattern.compile("\\d{6}");
    private static final Pattern TICKET_COUNT_ANSWER = Pattern.compile(
            "^(?<count>[1-9]|1\\d|20|[一二两三四五六七八九十]{1,3}|(?i:one|two|three|four|five|six|seven|eight|nine|ten))"
                    + "(?:个(?:人)?|人|位|张(?:票)?|票|(?i:person|people|tickets?))?$");
    private static final Pattern WEEKDAY_ANSWER = Pattern.compile(
            "^(?<week>本周|这周|下周)?(?:周|星期)(?<day>[一二三四五六日天])$");
    private static final Pattern WEEKEND_ANSWER = Pattern.compile("^(?<week>本周|这周|下周)?(?:周末|末)$");
    private static final Pattern ISO_DATE_ANSWER = Pattern.compile(
            "^(?<year>\\d{4})-(?<month>\\d{1,2})-(?<day>\\d{1,2})$");
    private static final Pattern MONTH_DAY_ANSWER = Pattern.compile(
            "^(?<month>\\d{1,2})(?:月|[.．/])(?<day>\\d{1,2})(?:日|号)?$");
    private static final Pattern CHINESE_MONTH_DAY_ANSWER = Pattern.compile(
            "^(?<month>[一二两三四五六七八九十]{1,3})月(?<day>[一二三四五六七八九十]{1,3})(?:日|号)?$");
    private static final Pattern DATE_IN_TEXT = Pattern.compile(
            "大后天|后天|明天|明晚|今天|今晚|(?:本周|这周|下周)?(?:周末|末)"
                    + "|(?:本周|这周|下周)?(?:周|星期)[一二三四五六日天]"
                    + "|(?<!\\d)\\d{4}-\\d{1,2}-\\d{1,2}(?!\\d)"
                    + "|(?<!\\d)\\d{1,2}(?:月|[.．/])\\d{1,2}(?:日|号)?(?!\\d)"
                    + "|[一二两三四五六七八九十]{1,3}月[一二三四五六七八九十]{1,3}(?:日|号)?");
    private static final Pattern TICKET_COUNT_IN_TEXT = Pattern.compile(
            "(?<count>[1-9]|1\\d|20|[一二两三四五六七八九十]{1,3}|(?i:one|two|three|four|five|six|seven|eight|nine|ten))"
                    + "(?:个(?:人)?|人|位|张(?:票)?|票|(?i:person|people|tickets?))");
    /** “我和一个人一起看”中的人数只表示同行者，总票数还要包含用户本人。 */
    private static final Pattern COMPANION_COUNT_IN_TEXT = Pattern.compile(
            "(?:和|跟|与)(?<count>[1-9]|1\\d|20|[一二两三四五六七八九十]{1,3})(?:个)?人(?:一起)?看");
    private static final Pattern CLOCK_TIME_IN_TEXT = Pattern.compile(
            "(?<period>上午|中午|下午|晚上|今晚)?(?<hour>\\d{1,2})[点时](?:(?<minute>\\d{1,2})分?)?"
                    + "|(?<clockHour>\\d{1,2})[：:](?<clockMinute>\\d{2})");
    private static final Pattern ANSWER_SUFFIX = Pattern.compile("[啊呀吧呢哦啦了]+$");
    private static final Pattern ANSWER_PUNCTUATION = Pattern.compile("[，。！？!?~～]+$");
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentConversationSlotRepository slotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AgentCityCodeResolver cityCodeResolver;
    private final MovieTitleResolutionTool movieTitleResolutionTool;
    private final CinemaTitleResolutionTool cinemaTitleResolutionTool;
    private final MovieGenreResolutionTool movieGenreResolutionTool;

    @Autowired
    public AgentConversationSlotService(CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository, AgentMessageRepository messageRepository,
            AgentConversationSlotRepository slotRepository, ObjectMapper objectMapper, Clock clock,
            AgentCityCodeResolver cityCodeResolver, MovieTitleResolutionTool movieTitleResolutionTool,
            CinemaTitleResolutionTool cinemaTitleResolutionTool, MovieGenreResolutionTool movieGenreResolutionTool) {
        this.currentUserAccessor = currentUserAccessor;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.slotRepository = slotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.cityCodeResolver = cityCodeResolver;
        this.movieTitleResolutionTool = movieTitleResolutionTool;
        this.cinemaTitleResolutionTool = cinemaTitleResolutionTool;
        this.movieGenreResolutionTool = movieGenreResolutionTool;
    }

    /** 保留现有轻量测试夹具；生产构造函数始终注入真实影片解析工具。 */
    public AgentConversationSlotService(CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository, AgentMessageRepository messageRepository,
            AgentConversationSlotRepository slotRepository, ObjectMapper objectMapper, Clock clock,
            AgentCityCodeResolver cityCodeResolver) {
        this(currentUserAccessor, sessionRepository, messageRepository, slotRepository, objectMapper, clock,
                cityCodeResolver, null, null, null);
    }

    /** 返回本轮唯一可用的服务端快照；无效回答保留旧值，由正常计划流程再次追问。 */
    @Transactional
    public SlotSnapshot prepare(String sessionId, String content, String entry) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentSession session = sessionRepository.findBySessionIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (session.status() != AgentSessionStatus.ACTIVE || !session.expireAt().isAfter(now())) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        PreparedSlots prepared = prepareLocked(session, userId, content, entry);
        persistLocked(session, userId, prepared);
        return prepared.snapshot();
    }

    /** 会话已由调用方加锁；这里只计算候选快照，不提前改写会话。 */
    PreparedSlots prepareLocked(AgentSession session, long userId, String content, String entry) {
        PersistedConversation current = slotRepository.findBySessionIdAndUserId(session.sessionId(), userId)
                .map(this::read).orElse(PersistedConversation.empty());
        String requestedSlot = latestQuestionSlot(session, userId);
        PersistedConversation next = accepted(current, requestedSlot, content);
        Map<String, String> values = new LinkedHashMap<>(next.values());
        if (entry != null && !entry.isBlank()) {
            values.put("context.entry", entry);
        }
        AgentConversationContext conversationContext = new AgentConversationContext(
                next.originalRequest(), requestedSlot == null ? null : AgentIntent.MOVIE);
        return new PreparedSlots(new SlotSnapshot(next.version(), values), conversationContext,
                !next.equals(current), write(next));
    }

    /** 与 run 占用共用同一事务；失败时槽位更新随事务一起回滚。 */
    void persistLocked(AgentSession session, long userId, PreparedSlots prepared) {
        if (session.activeRunId() != null) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        if (prepared.changed()
                && !slotRepository.update(session.id(), userId, session.version(), prepared.persistedJson())) {
            throw new IllegalStateException("会话槽位已被并发更新");
        }
    }

    private String latestQuestionSlot(AgentSession session, long userId) {
        return messageRepository.findBySessionIdAndUserId(session.id(), userId, 1).stream()
                .findFirst().filter(message -> message.type().name().equals("QUESTION"))
                .map(AgentMessage::payload).filter(java.util.Objects::nonNull).map(payload -> payload.value())
                .map(this::questionSlot).orElse(null);
    }

    private String questionSlot(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            String kind = root.path("questionKind").asText(root.path("kind").asText());
            String slot = slotForQuestionKind(kind);
            if (slot != null) {
                return slot;
            }
            // 只读取存量旧消息；新 QUESTION 不再保存工具字段名。
            String legacySlot = root.path("missingSlot").asText();
            return isConversationSlot(legacySlot) ? legacySlot : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private PersistedConversation accepted(PersistedConversation current, String slot, String value) {
        if (value == null) {
            return current;
        }
        Map<String, String> values = new LinkedHashMap<>(current.values());
        if (slot != null) {
            String normalized = normalizeUserAnswer(slot, value);
            if (normalized != null) {
                values.put(slot, normalized);
            }
        }
        // 一句话中的显式城市、日期、人数和类型一次性填入；后续只追问真正缺失的槽位。
        String cityCode = resolveCityCode(value);
        if (cityCode != null) {
            values.put("cityCode", cityCode);
        }
        String date = extractDate(value);
        if (date != null) {
            values.put("date", date);
        }
        String ticketCount = extractTicketCount(value);
        if (ticketCount != null) {
            values.put("ticketCount", ticketCount);
        }
        String genres = extractGenres(value);
        if (genres != null) {
            values.put("genres", genres);
        }
        boolean explicitMovieRequest = slot == null && containsMovieRequest(value);
        String[] timeRange = extractTimeRange(value);
        if (timeRange != null) {
            values.put("timeFrom", timeRange[0]);
            if (timeRange[1] != null) {
                values.put("timeTo", timeRange[1]);
            }
        } else if (explicitMovieRequest) {
            // 新观影请求未说明时段时，不能把上一次的上午、下午或具体钟点继续套用。
            values.remove("timeFrom");
            values.remove("timeTo");
        }
        if (movieTitleResolutionTool != null && explicitMovieRequest) {
            var resolvedMovie = movieTitleResolutionTool.resolve(value);
            if (resolvedMovie.isPresent()) {
                values.put("movieId", resolvedMovie.orElseThrow().movieId());
            } else {
                // 新的观影请求没有唯一目录命中时，不能把上一部影片的内部 ID 偷带到本轮推荐里。
                values.remove("movieId");
            }
        }
        if (cinemaTitleResolutionTool != null && explicitMovieRequest && containsCinemaRequest(value)) {
            String cityCodeForCinema = values.get("cityCode");
            var resolvedCinema = cinemaTitleResolutionTool.resolve(value, cityCodeForCinema);
            if (resolvedCinema.isPresent()) {
                values.put("cinemaId", resolvedCinema.orElseThrow().cinemaId());
            } else {
                values.remove("cinemaId");
            }
        } else if (explicitMovieRequest) {
            // 用户没有指定影院时，应在当前城市范围内重新找场次，不能沿用上一方案的影院 ID。
            values.remove("cinemaId");
        }
        // 没有待回答 QUESTION 时，这是新任务的原始语义；它单独保存，绝不混进可信槽位。
        String originalRequest = slot == null ? value : current.originalRequest();
        if (values.equals(current.values()) && java.util.Objects.equals(originalRequest, current.originalRequest())) {
            return current;
        }
        return new PersistedConversation(current.version() + 1, values, originalRequest);
    }

    private String normalizeUserAnswer(String slot, String value) {
        String text = value.trim();
        return switch (slot) {
            case "cityCode" -> resolveCityCode(text);
            case "date" -> normalizeDate(text);
            case "ticketCount" -> normalizeTicketCount(text);
            case "timeFrom", "timeTo" -> normalizeTime(text);
            default -> null;
        };
    }

    private String resolveCityCode(String text) {
        return cityCodeResolver.resolveCityCode(text).orElse(null);
    }

    private String normalizeDate(String value) {
        String text = normalizeShortAnswer(value);
        LocalDate today = LocalDate.now(clock.withZone(ClockConfiguration.BUSINESS_ZONE_ID));
        LocalDate parsed = switch (text) {
            case "今天", "今晚" -> today;
            case "明天", "明晚" -> today.plusDays(1L);
            case "后天" -> today.plusDays(2L);
            case "大后天" -> today.plusDays(3L);
            default -> parseNamedDate(text, today);
        };
        if (parsed != null) {
            return parsed.isBefore(today) ? null : parsed.toString();
        }
        return null;
    }

    private String extractDate(String value) {
        java.util.regex.Matcher matcher = DATE_IN_TEXT.matcher(value);
        return matcher.find() ? normalizeDate(matcher.group()) : null;
    }

    private static LocalDate parseWeekday(String text, LocalDate today) {
        java.util.regex.Matcher matcher = WEEKDAY_ANSWER.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        DayOfWeek target = switch (matcher.group("day")) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            case "日", "天" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalStateException("星期解析结果无效");
        };
        String week = matcher.group("week");
        if ("下周".equals(week)) {
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(1L).with(TemporalAdjusters.nextOrSame(target));
        }
        LocalDate candidate = "本周".equals(week) || "这周".equals(week)
                ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .with(TemporalAdjusters.nextOrSame(target))
                : today.with(TemporalAdjusters.nextOrSame(target));
        return candidate.isBefore(today) ? null : candidate;
    }

    /**
     * 日期槽位统一保存为 ISO 日期。过去的月日不会自动滚到下一年，避免用户输入“8.8”
     * 却被悄悄改成明年的同一天；要看明年时必须明确给出年份。
     */
    private static LocalDate parseNamedDate(String text, LocalDate today) {
        LocalDate weekday = parseWeekday(text, today);
        if (weekday != null) {
            return weekday;
        }
        java.util.regex.Matcher weekend = WEEKEND_ANSWER.matcher(text);
        if (weekend.matches()) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if ("下周".equals(weekend.group("week"))) {
                monday = monday.plusWeeks(1L);
            }
            return monday.plusDays(5L);
        }
        java.util.regex.Matcher iso = ISO_DATE_ANSWER.matcher(text);
        if (iso.matches()) {
            return safeDate(iso.group("year"), iso.group("month"), iso.group("day"));
        }
        java.util.regex.Matcher monthDay = MONTH_DAY_ANSWER.matcher(text);
        if (monthDay.matches()) {
            return safeDate(Integer.toString(today.getYear()), monthDay.group("month"), monthDay.group("day"));
        }
        java.util.regex.Matcher chineseMonthDay = CHINESE_MONTH_DAY_ANSWER.matcher(text);
        if (chineseMonthDay.matches()) {
            Integer month = chineseOrArabicNumber(chineseMonthDay.group("month"));
            Integer day = chineseOrArabicNumber(chineseMonthDay.group("day"));
            return month == null || day == null ? null : safeDate(today.getYear(), month, day);
        }
        return null;
    }

    private static LocalDate safeDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (NumberFormatException | java.time.DateTimeException exception) {
            return null;
        }
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException exception) {
            return null;
        }
    }

    private static String normalizeTicketCount(String value) {
        String text = normalizeShortAnswer(value).replace(" ", "");
        java.util.regex.Matcher matcher = TICKET_COUNT_ANSWER.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Integer count = chineseOrArabicNumber(matcher.group("count"));
        return count != null && count >= 1 && count <= 20 ? Integer.toString(count) : null;
    }

    private static String extractTicketCount(String value) {
        String normalized = value.replace(" ", "");
        java.util.regex.Matcher companionMatcher = COMPANION_COUNT_IN_TEXT.matcher(normalized);
        if (companionMatcher.find()) {
            Integer companions = chineseOrArabicNumber(companionMatcher.group("count"));
            int total = companions == null ? 0 : companions + 1;
            return total >= 1 && total <= 20 ? Integer.toString(total) : null;
        }
        java.util.regex.Matcher matcher = TICKET_COUNT_IN_TEXT.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        Integer count = chineseOrArabicNumber(matcher.group("count"));
        return count != null && count >= 1 && count <= 20 ? Integer.toString(count) : null;
    }

    private static String[] extractTimeRange(String value) {
        java.util.regex.Matcher matcher = CLOCK_TIME_IN_TEXT.matcher(value);
        if (matcher.find()) {
            int hour = matcher.group("clockHour") == null
                    ? Integer.parseInt(matcher.group("hour")) : Integer.parseInt(matcher.group("clockHour"));
            int minute = matcher.group("clockMinute") == null
                    ? matcher.group("minute") == null ? 0 : Integer.parseInt(matcher.group("minute"))
                    : Integer.parseInt(matcher.group("clockMinute"));
            String period = matcher.group("period");
            if (("下午".equals(period) || "晚上".equals(period) || "今晚".equals(period)) && hour < 12) {
                hour += 12;
            }
            if (hour <= 23 && minute <= 59) {
                LocalTime time = LocalTime.of(hour, minute);
                LocalTime end = time.equals(LocalTime.of(23, 59)) ? LocalTime.MAX : time.plusMinutes(1);
                return new String[] {time.toString(), end.toString()};
            }
        }
        if (value.contains("下午")) {
            return new String[] {"12:00", "18:00"};
        }
        if (value.contains("晚上") || value.contains("今晚")) {
            return new String[] {"18:00", "23:59"};
        }
        if (value.contains("上午")) {
            return new String[] {"06:00", "12:00"};
        }
        return null;
    }

    private static String normalizeTime(String value) {
        try {
            return LocalTime.parse(value).toString();
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private String extractGenres(String value) {
        List<String> genres = movieGenreResolutionTool == null ? List.of() : movieGenreResolutionTool.resolve(value);
        if (genres.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(genres);
        } catch (Exception exception) {
            throw new IllegalStateException("观影类型序列化失败", exception);
        }
    }

    private static boolean containsMovieRequest(String value) {
        return value.contains("看") || value.contains("电影") || value.contains("影片");
    }

    private static boolean containsCinemaRequest(String value) {
        return value.contains("影院") || value.contains("影城") || value.contains("电影院");
    }

    private static Integer chineseOrArabicNumber(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        Integer english = switch (normalized) {
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            case "six" -> 6;
            case "seven" -> 7;
            case "eight" -> 8;
            case "nine" -> 9;
            case "ten" -> 10;
            default -> null;
        };
        if (english != null) {
            return english;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.valueOf(value);
        }
        if ("十".equals(value)) {
            return 10;
        }
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : chineseDigit(value.charAt(0));
            int units = ten == value.length() - 1 ? 0 : chineseDigit(value.charAt(ten + 1));
            return tens < 0 || units < 0 ? null : tens * 10 + units;
        }
        int digit = value.length() == 1 ? chineseDigit(value.charAt(0)) : -1;
        return digit < 0 ? null : digit;
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static String normalizeShortAnswer(String value) {
        String normalized = value.strip();
        String previous;
        do {
            previous = normalized;
            normalized = ANSWER_PUNCTUATION.matcher(normalized).replaceFirst("");
            normalized = ANSWER_SUFFIX.matcher(normalized).replaceFirst("");
        } while (!normalized.equals(previous));
        return normalized.strip();
    }

    private java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    private PersistedConversation read(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            long version = root.path("version").canConvertToLong() ? root.path("version").asLong() : 0L;
            Map<String, String> values = new LinkedHashMap<>();
            for (String slot : List.of("cityCode", "date", "ticketCount", "genres", "movieId", "cinemaId", "timeFrom", "timeTo")) {
                String value = root.path("values").path(slot).isTextual()
                        ? normalizePersistedValue(slot, root.path("values").path(slot).asText()) : null;
                if (value != null) {
                    values.put(slot, value);
                }
            }
            String originalRequest = root.path("context").path("originalRequest").isTextual()
                    ? root.path("context").path("originalRequest").asText() : null;
            return new PersistedConversation(version, values, originalRequest);
        } catch (Exception exception) {
            return PersistedConversation.empty();
        }
    }

    private String write(PersistedConversation conversation) {
        try {
            Map<String, Object> persisted = new LinkedHashMap<>();
            persisted.put("version", conversation.version());
            persisted.put("values", conversation.values());
            if (conversation.originalRequest() != null) {
                persisted.put("context", Map.of("originalRequest", conversation.originalRequest()));
            }
            return objectMapper.writeValueAsString(persisted);
        } catch (Exception exception) {
            throw new IllegalStateException("会话槽位序列化失败", exception);
        }
    }

    private static boolean isConversationSlot(String value) {
        return "cityCode".equals(value) || "date".equals(value) || "ticketCount".equals(value);
    }

    private static String slotForQuestionKind(String value) {
        return switch (value) {
            case "CITY" -> "cityCode";
            case "DATE" -> "date";
            case "TICKET_COUNT" -> "ticketCount";
            default -> null;
        };
    }

    private String normalizePersistedValue(String slot, String value) {
        if ("cityCode".equals(slot)) {
            return CITY_CODE.matcher(value).matches() ? value : null;
        }
        if ("genres".equals(slot)) {
            try {
                JsonNode genres = objectMapper.readTree(value);
                if (!genres.isArray()) {
                    return null;
                }
                List<String> normalized = new java.util.ArrayList<>();
                genres.forEach(genre -> {
                    if (genre.isTextual()) {
                        String genreValue = genre.asText().strip();
                        if (!genreValue.isEmpty() && genreValue.length() <= 64) {
                            normalized.add(genreValue);
                        }
                    }
                });
                return normalized.isEmpty() ? null : objectMapper.writeValueAsString(normalized);
            } catch (Exception exception) {
                return null;
            }
        }
        if ("movieId".equals(slot) || "cinemaId".equals(slot)) {
            return value.matches("[1-9]\\d*") ? value : null;
        }
        if ("timeFrom".equals(slot) || "timeTo".equals(slot)) {
            return normalizeTime(value);
        }
        return normalizeUserAnswer(slot, value);
    }

    private record PersistedConversation(long version, Map<String, String> values, String originalRequest) {
        private static PersistedConversation empty() {
            return new PersistedConversation(0L, Map.of(), null);
        }

        private PersistedConversation {
            values = Map.copyOf(values);
            originalRequest = originalRequest == null || originalRequest.isBlank() ? null : originalRequest;
        }
    }

    record PreparedSlots(SlotSnapshot snapshot, AgentConversationContext conversationContext,
            boolean changed, String persistedJson) {
    }
}
