package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 将用户提到的影院名称解析为当前城市目录中的可信影院 ID，不允许模型生成或选择 cinemaId。 */
@Service
public class CinemaTitleResolutionTool {
    private final ContentQueryService contentQueryService;

    public CinemaTitleResolutionTool(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    /** 仅唯一命中才返回，目录不可用、没有命中或存在歧义时都不猜测影院 ID。 */
    public Optional<ResolvedCinema> resolve(String userInput, String cityCode) {
        String input = normalize(userInput);
        if (input.length() < 2 || cityCode == null || !cityCode.matches("[1-9]\\d{5}")) {
            return Optional.empty();
        }
        try {
            var catalog = contentQueryService.query(
                    new ContentQuery(ContentResourceType.CINEMA, null, cityCode, null));
            List<ResolvedCinema> matches = new ArrayList<>();
            catalog.data().stream().map(CinemaContent.class::cast)
                    .filter(cinema -> cinema.cinemaId() != null && matchesName(input, cinema.name()))
                    .forEach(cinema -> matches.add(
                            new ResolvedCinema(Long.toString(cinema.cinemaId()), cinema.name())));
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean matchesName(String input, String name) {
        String normalizedName = normalize(name);
        if (normalizedName.length() >= 4 && input.contains(normalizedName)) {
            return true;
        }
        String abbreviated = normalizedName
                .replaceFirst("^(?:[\\p{IsHan}]{2,6}市)?", "")
                .replaceAll("(?:电影院|影城|影城店|影院)$", "");
        return abbreviated.length() >= 4 && input.contains(abbreviated);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s《》〈〉『』「」\"'，。,.]", "");
    }

    public record ResolvedCinema(String cinemaId, String name) {
    }
}
