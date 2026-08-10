package com.miaoyu.ticket.agent.application.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 从当前影片目录提取用户明确提到的类型。
 *
 * <p>不维护 Agent 自己的类型白名单；影片目录更新了类型后，下一轮对话会自动使用新类型。</p>
 */
@Service
public class MovieGenreResolutionTool {
    private final ContentQueryService contentQueryService;
    private final ObjectMapper objectMapper;

    public MovieGenreResolutionTool(ContentQueryService contentQueryService, ObjectMapper objectMapper) {
        this.contentQueryService = contentQueryService;
        this.objectMapper = objectMapper;
    }

    /** 只返回目录中存在且被用户原文明确提到的类型；目录不可用或资料损坏时返回空列表。 */
    public List<String> resolve(String userInput) {
        String input = normalize(userInput);
        if (input.isEmpty()) {
            return List.of();
        }
        try {
            var catalog = contentQueryService.queryLocalMovies(null, null);
            if (catalog.isEmpty()) {
                return List.of();
            }
            Set<String> knownGenres = new LinkedHashSet<>();
            catalog.orElseThrow().data().stream().map(MovieContent.class::cast)
                    .forEach(movie -> collectGenres(movie.genresJson(), knownGenres));
            return knownGenres.stream()
                    .filter(genre -> normalize(genre).length() >= 2 && input.contains(normalize(genre)))
                    .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private void collectGenres(String genresJson, Set<String> knownGenres) {
        try {
            JsonNode genres = objectMapper.readTree(genresJson);
            if (!genres.isArray()) {
                return;
            }
            genres.forEach(genre -> {
                if (genre.isTextual()) {
                    String value = genre.asText().strip();
                    if (!value.isEmpty()) {
                        knownGenres.add(value);
                    }
                }
            });
        } catch (Exception ignored) {
            // 单条目录资料损坏时不影响其他影片类型的提取。
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
