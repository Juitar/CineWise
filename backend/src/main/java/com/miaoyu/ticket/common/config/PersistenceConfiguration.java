package com.miaoyu.ticket.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 公共插件；业务排序字段仍必须由接口级白名单约束。 */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    public BusinessIdGenerator businessIdGenerator() {
        return IdWorker::getId;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(ApiProperties apiProperties) {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit((long) apiProperties.maxPageSize());
        pagination.setOverflow(false);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
