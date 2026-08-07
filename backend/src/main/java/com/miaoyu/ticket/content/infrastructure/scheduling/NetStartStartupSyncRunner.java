package com.miaoyu.ticket.content.infrastructure.scheduling;

import com.miaoyu.ticket.content.application.ContentSyncService;
import com.miaoyu.ticket.content.application.CityResolutionService;
import com.miaoyu.ticket.content.infrastructure.provider.NetStartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 受控测试窗口的单次真实内容同步入口。
 *
 * <p>它只在显式配置后随应用启动调用一次，避免为了临时验证而暴露公网刷新接口。
 * 正式环境保持默认关闭；Provider 自身仍会拒绝非 dev/demo profile。</p>
 */
@Component
@ConditionalOnProperty(prefix = "cinewise.content.netstart", name = "sync-on-startup", havingValue = "true")
public class NetStartStartupSyncRunner implements ApplicationRunner {

    /** 日志只记录本次同步数量，避免记录第三方原始内容或访问参数。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(NetStartStartupSyncRunner.class);

    /** 运行开关由绑定后的配置对象提供，不能由 HTTP 请求临时覆盖。 */
    private final NetStartProperties properties;
    /** 应用服务负责事务、身份隔离和缓存提交顺序，Runner 不直接访问数据库或 Redis。 */
    private final ContentSyncService contentSyncService;
    private final CityResolutionService cityResolutionService;

    @org.springframework.beans.factory.annotation.Autowired
    public NetStartStartupSyncRunner(NetStartProperties properties, ContentSyncService contentSyncService,
                                     CityResolutionService cityResolutionService) {
        this.properties = properties;
        this.contentSyncService = contentSyncService;
        this.cityResolutionService = cityResolutionService;
    }

    /** 兼容旧单元测试；正式 Spring 构造器同时注入城市目录。 */
    NetStartStartupSyncRunner(NetStartProperties properties, ContentSyncService contentSyncService) {
        this.properties = properties;
        this.contentSyncService = contentSyncService;
        this.cityResolutionService = null;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        // ApplicationRunner 在上下文就绪后执行，避免 Provider 早于数据源和 Redis 初始化。
        synchronizeOnce();
    }

    /**
     * 只有 Provider 已显式开启时才允许调用同步服务。
     *
     * <p>开关组合写错时不访问外网、不写库，保留默认 Demo 回退，避免一次性测试开关意外改变运行行为。</p>
     */
    public void synchronizeOnce() {
        if (!properties.enabled()) {
            LOGGER.warn("NetStart 启动同步未执行：Provider 未开启");
            return;
        }
        // 返回数量只用于测试窗口观察，不改变后续页面查询或票务事实。
        int synchronizedCount = contentSyncService.synchronizeDailyContent();
        if (cityResolutionService != null) {
            properties.syncCities().forEach(cityCode -> cityResolutionService.findCityName(cityCode)
                    .ifPresent(cityName -> contentSyncService.synchronizeCityCinemasWithResult(
                            cityName, cityCode, () -> true)));
        }
        LOGGER.info("NetStart 启动同步已执行一次: synchronizedCount={}", synchronizedCount);
    }
}
