package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.SeedProperties;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 初始化内容模块拥有的固定演示数据，只向票务模块返回稳定标识，不暴露持久化对象。 */
@Service
public class ContentSeedApplicationService {

    private static final List<MovieTemplate> MOVIES = List.of(
            new MovieTemplate("mock-movie-01", "星河远征", "[\"科幻\",\"冒险\"]", 128, "8.6"),
            new MovieTemplate("mock-movie-02", "夏日回声", "[\"剧情\",\"青春\"]", 112, "8.1"),
            new MovieTemplate("mock-movie-03", "云端来信", "[\"爱情\",\"剧情\"]", 105, "7.9"),
            new MovieTemplate("mock-movie-04", "午夜追光", "[\"悬疑\",\"犯罪\"]", 118, "8.3"),
            new MovieTemplate("mock-movie-05", "小城奇遇", "[\"喜剧\",\"家庭\"]", 101, "7.8"),
            new MovieTemplate("mock-movie-06", "深海之歌", "[\"动画\",\"奇幻\"]", 96, "8.5"),
            new MovieTemplate("mock-movie-07", "长风万里", "[\"动作\",\"历史\"]", 132, "8.0"),
            new MovieTemplate("mock-movie-08", "时间拼图", "[\"科幻\",\"悬疑\"]", 121, "8.4"),
            new MovieTemplate("mock-movie-09", "山野星光", "[\"纪录\",\"自然\"]", 89, "8.7"),
            new MovieTemplate("mock-movie-10", "周末乐队", "[\"音乐\",\"喜剧\"]", 108, "7.7"));

    private static final List<CinemaTemplate> CINEMAS = List.of(
            new CinemaTemplate("mock-cinema-01", "妙语影城·滨江店", "滨江区", "江南大道88号", "120.2120100", "30.2084000"),
            new CinemaTemplate("mock-cinema-02", "妙语影城·西湖店", "西湖区", "文三路168号", "120.1302600", "30.2741500"),
            new CinemaTemplate("mock-cinema-03", "妙语影城·拱墅店", "拱墅区", "湖墅南路258号", "120.1509500", "30.3182200"),
            new CinemaTemplate("mock-cinema-04", "妙语影城·上城店", "上城区", "钱江路66号", "120.2057100", "30.2573900"));

    private final ContentSeedRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final SeedProperties properties;
    private final Clock clock;

    public ContentSeedApplicationService(
            ContentSeedRepository repository,
            BusinessIdGenerator idGenerator,
            SeedProperties properties,
            Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 幂等补齐固定影片和影院；相同随机种子只影响数据排列，不改变业务唯一键。
     *
     * @return 供票务种子引用的影片、影院稳定标识
     */
    @Transactional
    public ContentSeedCatalog ensureFixedSeed() {
        LocalDateTime generatedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);

        List<MovieTemplate> movieOrder = new ArrayList<>(MOVIES);
        List<CinemaTemplate> cinemaOrder = new ArrayList<>(CINEMAS);
        Collections.shuffle(movieOrder, new Random(properties.fixedValue()));
        Collections.shuffle(cinemaOrder, new Random(properties.fixedValue() ^ 0x5DEECE66DL));

        List<ContentSeedCatalog.MovieRef> movies = movieOrder.stream()
                .map(template -> ensureMovie(template, generatedAt))
                .toList();
        List<ContentSeedCatalog.CinemaRef> cinemas = cinemaOrder.stream()
                .map(template -> ensureCinema(template, generatedAt))
                .toList();
        return new ContentSeedCatalog(movies, cinemas);
    }

    private ContentSeedCatalog.MovieRef ensureMovie(
            MovieTemplate template,
            LocalDateTime generatedAt) {
        long movieId = repository.ensureMovie(new ContentSeedRepository.MovieSeed(
                idGenerator.nextId(),
                template.sourceMovieId(),
                template.title(),
                template.genresJson(),
                template.durationMinutes(),
                new BigDecimal(template.rating()),
                generatedAt,
                null));
        return new ContentSeedCatalog.MovieRef(movieId, template.sourceMovieId(), template.durationMinutes());
    }

    private ContentSeedCatalog.CinemaRef ensureCinema(
            CinemaTemplate template,
            LocalDateTime generatedAt) {
        long cinemaId = repository.ensureCinema(new ContentSeedRepository.CinemaSeed(
                idGenerator.nextId(),
                template.sourceCinemaId(),
                template.name(),
                "330100",
                template.area(),
                template.address(),
                new BigDecimal(template.longitude()),
                new BigDecimal(template.latitude()),
                generatedAt,
                null));
        return new ContentSeedCatalog.CinemaRef(cinemaId, template.sourceCinemaId());
    }

    private record MovieTemplate(
            String sourceMovieId,
            String title,
            String genresJson,
            int durationMinutes,
            String rating) {
    }

    private record CinemaTemplate(
            String sourceCinemaId,
            String name,
            String area,
            String address,
            String longitude,
            String latitude) {
    }
}
