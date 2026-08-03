package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化内容模块拥有的固定演示数据。
 *
 * <p>这里是 D 维护内容清单与 A 维护票务种子的唯一交接点。服务只返回已经落库的实际 ID，
 * 使 A 无需读取内容表或了解 Provider、缓存等实现细节。</p>
 *
 * <p>来源 ID 才是跨环境稳定身份；数据库雪花 ID 只能在首次写入时生成，不能写进
 * `demo-content-v1` 资源，否则不同空库会产生错误关联。</p>
 *
 * <p>本服务不生成影厅、场次、票价、座位或库存；这些票务事实仍由 A 在内容目录返回后创建。
 * 内容种子失败时也不得绕过该边界直接写入票务表。</p>
 */
@Service
public class ContentSeedApplicationService {

    /** 内容表写入端口，种子服务不得直接拼接 SQL。 */
    private final ContentSeedRepository repository;

    /** D 的版本化清单读取端口；具体资源文件读取属于后续基础设施实现。 */
    private final DemoContentCatalogProvider catalogProvider;

    /** 首次缺少记录时生成内部主键，重复执行时该值不会覆盖已有主键。 */
    private final BusinessIdGenerator idGenerator;

    /** 统一业务时钟，保证演示时间与自动化测试可以稳定复现。 */
    private final Clock clock;

    public ContentSeedApplicationService(
            ContentSeedRepository repository,
            DemoContentCatalogProvider catalogProvider,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.catalogProvider = catalogProvider;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 幂等补齐固定影片和影院。
     *
     * <p>资源清单顺序可以作为 Demo 查询和测试夹具的稳定顺序，但不能把该顺序当作数据库
     * 主键。Repository 按来源 ID 查回已经存在的记录，避免初始化第二次时改变 A 已引用的 ID。</p>
     *
     * @return 供票务种子引用的影片、影院实际数据库标识
     */
    @Transactional
    public ContentSeedCatalog ensureFixedSeed() {
        LocalDateTime generatedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        DemoContentCatalog catalog = catalogProvider.load();
        List<ContentSeedCatalog.MovieRef> movies = catalog.movies().stream()
                .map(template -> ensureMovie(template, generatedAt))
                .toList();
        List<ContentSeedCatalog.CinemaRef> cinemas = catalog.cinemas().stream()
                .map(template -> ensureCinema(template, generatedAt))
                .toList();
        return new ContentSeedCatalog(movies, cinemas);
    }

    /**
     * 写入或查回影片后再返回主键；票务侧只得到必要引用，不能获得内容持久化对象。
     */
    private ContentSeedCatalog.MovieRef ensureMovie(
            MovieContent template,
            LocalDateTime generatedAt) {
        long movieId = repository.ensureMovie(new ContentSeedRepository.MovieSeed(
                idGenerator.nextId(),
                template.sourceMovieId(),
                template.title(),
                template.genresJson(),
                template.durationMinutes(),
                template.rating(),
                generatedAt,
                null));
        return new ContentSeedCatalog.MovieRef(movieId, template.sourceMovieId(), template.durationMinutes());
    }

    /**
     * 影院与影片使用相同的幂等原则，保证同一来源 ID 不会对应两条内容记录。
     */
    private ContentSeedCatalog.CinemaRef ensureCinema(
            CinemaContent template,
            LocalDateTime generatedAt) {
        long cinemaId = repository.ensureCinema(new ContentSeedRepository.CinemaSeed(
                idGenerator.nextId(),
                template.sourceCinemaId(),
                template.name(),
                template.cityCode(),
                template.area(),
                template.address(),
                template.longitude(),
                template.latitude(),
                generatedAt,
                null));
        return new ContentSeedCatalog.CinemaRef(cinemaId, template.sourceCinemaId());
    }
}
