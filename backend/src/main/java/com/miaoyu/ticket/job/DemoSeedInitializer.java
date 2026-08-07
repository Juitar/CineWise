package com.miaoyu.ticket.job;

import com.miaoyu.ticket.content.application.ContentSeedApplicationService;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.ticketing.application.TicketingSeedApplicationService;
import com.miaoyu.ticket.ticketing.application.TicketingSeedReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 仅在显式开启时运行的固定演示数据启动初始化器。 */
@Component
@ConditionalOnProperty(prefix = "cinewise.seed", name = "enabled", havingValue = "true")
public class DemoSeedInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeedInitializer.class);

    private final ContentSeedApplicationService contentSeedService;
    private final ContentPurchaseQueryPort contentPurchaseQueryPort;
    private final TicketingSeedApplicationService ticketingSeedService;

    public DemoSeedInitializer(
            ContentSeedApplicationService contentSeedService,
            ContentPurchaseQueryPort contentPurchaseQueryPort,
            TicketingSeedApplicationService ticketingSeedService) {
        this.contentSeedService = contentSeedService;
        this.contentPurchaseQueryPort = contentPurchaseQueryPort;
        this.ticketingSeedService = ticketingSeedService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        initialize();
    }

    /**
     * 按“内容主键先就绪、票务数据后引用”的顺序执行完整固定种子初始化。
     *
     * @return 初始化完成后应达到的票务数据规模
     */
    public TicketingSeedReport initialize() {
        // 内容数据必须先产生稳定主键，票务场次才能通过逻辑外键引用影片和影院。
        var catalog = contentSeedService.ensureFixedSeed();
        TicketingSeedReport report = ticketingSeedService.ensureFixedSeed(catalog);
        LOGGER.info(
                "固定演示种子初始化完成: movies={}, cinemas={}, auditoriums={}, shows={}, seats={}",
                catalog.movies().size(),
                catalog.cinemas().size(),
                report.auditoriumCount(),
                report.showCount(),
                report.seatCount());
        var liveCatalog = findLiveDemoCatalog();
        if (liveCatalog == null) {
            return report;
        }
        TicketingSeedReport liveReport = ticketingSeedService.ensureLiveDemoSeed(liveCatalog);
        if (liveReport.showCount() > 0) {
            LOGGER.info(
                    "长沙真实影院本地演示排期初始化完成: movies={}, cinemas={}, shows={}, seats={}",
                    liveCatalog.movies().size(),
                    liveCatalog.cinemas().size(),
                    liveReport.showCount(),
                    liveReport.seatCount());
        }
        return report;
    }

    /** 内容目录不可用不影响固定 Demo 的最小启动链路；其他业务异常仍交由启动器失败处理。 */
    private ContentPurchaseQueryPort.DemoPurchaseCatalog findLiveDemoCatalog() {
        try {
            return contentPurchaseQueryPort.findLiveDemoPurchaseCatalog("430100");
        } catch (BusinessException exception) {
            if (exception.getErrorCode().code() != 303004) {
                throw exception;
            }
            LOGGER.warn("真实内容目录暂不可用，跳过本轮真实影院 Mock 排期初始化");
            return null;
        }
    }
}
