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
        String[] segments = title.split(TITLE_SEPARATOR, 2);
        String leadingTitle = normalize(segments[0]);
        return leadingTitle.length() >= 2 && input.contains(leadingTitle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s《》〈〉『』「」\"'，。,.]", "");
    }

    public record ResolvedMovie(String movieId, String title) {
    }
}
