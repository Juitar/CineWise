package com.miaoyu.ticket.admin.infrastructure;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C的用户目录实现尚未合入时提供失败关闭的安全兜底。
 *
 * <p>兜底绝不查询sys_user、返回空集合或伪造脱敏邮箱；所有调用统一以
 * 301002/503失败。C注册正式UserAdminQueryPort Bean后该配置自动让位。</p>
 */
@Configuration(proxyBeanMethods = false)
public class UserAdminQueryPortFallbackConfiguration {

    /** 保证其他模块测试和应用启动不因可选联调实现缺失而装配失败。 */
    @Bean
    @ConditionalOnMissingBean(UserAdminQueryPort.class)
    public UserAdminQueryPort unavailableUserAdminQueryPort() {
        return new UnavailableUserAdminQueryPort();
    }

    /** 两个读取入口保持同一失败语义，禁止部分结果形成误导性管理视图。 */
    private static final class UnavailableUserAdminQueryPort implements UserAdminQueryPort {

        @Override
        public Set<Long> findUserIdsByKeyword(String userKeyword) {
            throw unavailable();
        }

        @Override
        public Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds) {
            throw unavailable();
        }

        private BusinessException unavailable() {
            return new BusinessException(AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);
        }
    }
}
