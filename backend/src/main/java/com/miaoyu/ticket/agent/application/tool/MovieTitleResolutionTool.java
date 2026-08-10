package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 将用户提到的片名解析为内容目录中的可信影片 ID，不允许模型生成或选择 movieId。 */
@Service
public class MovieTitleResolutionTool {
    private static final String TITLE_SEPARATOR = "[：:！!？?·\\-]";
    private static final String OPTIONAL_TITLE_CHARACTERS = "的了着过地得吗呢吧啊呀哦嘛呐";
    private static final int MAX_OMITTED_TITLE_CHARACTERS = 2;
    private static final int MIN_TITLE_FRAGMENT_LENGTH = 3;

    private final ContentQueryService contentQueryService;

    public MovieTitleResolutionTool(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    /** 只有唯一命中时才返回；没有命中或存在歧义时绝不猜测影片 ID。 */
    public Optional<ResolvedMovie> resolve(String userInput) {
        String input = normalize(userInput);
        if (input.length() < 2) {
            return Optional.empty();
        }
        var catalog = contentQueryService.queryLocalMovies(null, null);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        List<ResolvedMovie> matches = new ArrayList<>();
        catalog.orElseThrow().data().stream()
                .map(MovieContent.class::cast)
                .filter(movie -> movie.movieId() != null && matchesTitle(input, movie.title()))
                .forEach(movie -> matches.add(new ResolvedMovie(Long.toString(movie.movieId()), movie.title())));
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static boolean matchesTitle(String input, String title) {
        String normalizedTitle = normalize(title);
        if (normalizedTitle.length() >= 2 && input.contains(normalizedTitle)) {
            return true;
        }
        if (matchesAfterOmittingTitleParticles(input, normalizedTitle)) {
            return true;
        }
        String[] segments = title.split(TITLE_SEPARATOR, 2);
        String leadingTitle = normalize(segments[0]);
        if (leadingTitle.length() >= 2 && (input.contains(leadingTitle)
                || matchesAfterOmittingTitleParticles(input, leadingTitle))) {
            return true;
        }
        // 用户常只说长片名的后半段，例如“我想看龙餐馆”。只能匹配目录标题结尾的
        // 完整中文片段，不能用“欢迎来”之类的公共前缀去猜测另一部影片。
        return hasTitleEndingFragment(extractTitlePart(input), normalizedTitle);
    }

    private static String extractTitlePart(String input) {
        String value = input;
        for (String prefix : List.of("我想要看", "我想看", "想要看", "想看", "我要看", "帮我找", "帮我推荐",
                "推荐", "找一部", "找个", "找", "看一下", "看下", "看")) {
            value = value.replace(prefix, "");
        }
        for (String suffix : List.of("电影", "影片", "吧", "呢", "吗")) {
            if (value.length() > suffix.length() && value.endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static boolean hasTitleEndingFragment(String input, String title) {
        int index = 0;
        while (index < input.length()) {
            if (!isChinese(input.charAt(index))) {
                index += 1;
                continue;
            }
            int end = index;
            while (end < input.length() && isChinese(input.charAt(end))) {
                end += 1;
            }
            String segment = input.substring(index, end);
            for (int start = 0; start <= segment.length() - MIN_TITLE_FRAGMENT_LENGTH; start += 1) {
                for (int fragmentEnd = start + MIN_TITLE_FRAGMENT_LENGTH; fragmentEnd <= segment.length();
                        fragmentEnd += 1) {
                    if (title.endsWith(segment.substring(start, fragmentEnd))) {
                        return true;
                    }
                }
            }
            index = end;
        }
        return false;
    }

    private static boolean isChinese(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    /**
     * 只容忍目录标题中极少量可省略虚词，不做错别字、近义词或编辑距离猜测。
     *
     * <p>例如目录为“欢迎来到龙餐馆”而用户说“欢迎来龙餐馆”时，允许省略“来到”中的“到”。
     * 候选影片仍由 {@link #resolve(String)} 的唯一命中规则最终把关。</p>
     */
    private static boolean matchesAfterOmittingTitleParticles(String input, String title) {
        if (title.length() < 4) {
            return false;
        }
        List<Integer> optionalIndices = new ArrayList<>();
        for (int index = 0; index < title.length(); index += 1) {
            if (isOptionalTitleCharacter(title, index)) {
                optionalIndices.add(index);
            }
        }
        for (int first : optionalIndices) {
            if (input.contains(withoutCharacters(title, first))) {
                return true;
            }
            for (int second : optionalIndices) {
                if (second <= first || MAX_OMITTED_TITLE_CHARACTERS < 2) {
                    continue;
                }
                if (input.contains(withoutCharacters(title, first, second))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOptionalTitleCharacter(String title, int index) {
        char character = title.charAt(index);
        return OPTIONAL_TITLE_CHARACTERS.indexOf(character) >= 0
                || (character == '到' && index > 0 && title.charAt(index - 1) == '来');
    }

    private static String withoutCharacters(String value, int... indices) {
        StringBuilder builder = new StringBuilder(value.length() - indices.length);
        for (int index = 0; index < value.length(); index += 1) {
            boolean omitted = false;
            for (int candidate : indices) {
                if (candidate == index) {
                    omitted = true;
                    break;
                }
            }
            if (!omitted) {
                builder.append(value.charAt(index));
            }
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s《》〈〉『』「」\"'，。,.]", "");
    }

    public record ResolvedMovie(String movieId, String title) {
    }
}
