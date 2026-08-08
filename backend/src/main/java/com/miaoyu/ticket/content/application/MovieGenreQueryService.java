package com.miaoyu.ticket.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 从内容资料中提取可用于画像的影片主类型。
 *
 * <p>支付事件给出的是 A 权威场次关联的电影 ID；影片类型仍由内容模块解释。查询失败必须降级为空，
 * 因为画像缺失不能影响已提交的支付结果，也不能让画像模块绕过公开 Application API 访问内容持久化层。</p>
 */
@Service
public class MovieGenreQueryService implements MovieGenreQueryPort {

    private final ContentQueryService contentQueryService;
    private final ObjectMapper objectMapper;

    public MovieGenreQueryService(ContentQueryService contentQueryService, ObjectMapper objectMapper) {
        this.contentQueryService = contentQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分类数组的第一个有效项是内容模块已标准化的主类型；不把原始 JSON 或多个类型传给画像模块。
     */
    @Override
    public Optional<String> findPrimaryGenre(String movieId) {
        Long parsedMovieId = parsePositiveMovieId(movieId);
        if (parsedMovieId == null) {
            return Optional.empty();
        }
        try {
            return contentQueryService.query(new ContentQuery(ContentResourceType.MOVIE, parsedMovieId, null, null))
                    .data().stream()
                    .map(MovieContent.class::cast)
                    .filter(movie -> parsedMovieId.equals(movie.movieId()))
                    .findFirst()
                    .flatMap(movie -> parsePrimaryGenre(movie.genresJson()));
        } catch (BusinessException exception) {
            // 内容目录暂不可用时只跳过标签，不能使支付后的画像事件处理失败。
            return Optional.empty();
        }
    }

    /** 支付事件边界使用字符串 ID；空值、前导空白和非正数均不能查询内容目录。 */
    private Long parsePositiveMovieId(String movieId) {
        if (movieId == null || movieId.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(movieId);
            return value > 0L ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 仅接受标准 JSON 数组中的非空文本，损坏内容资料不能伪造为画像标签。 */
    private Optional<String> parsePrimaryGenre(String genresJson) {
        try {
            JsonNode genres = objectMapper.readTree(genresJson);
            if (genres == null || !genres.isArray()) {
                return Optional.empty();
            }
            for (JsonNode genre : genres) {
                String value = genre.isTextual() ? genre.asText().trim() : "";
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
