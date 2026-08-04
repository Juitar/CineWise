package com.miaoyu.ticket.admin.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证MySQL集成测试在Spring上下文启动前只接受受控目标。 */
class AdminOrderQueryMySqlSafetyTest {

    @Test
    void givenLocalOrCiDatabaseUrl_whenValidate_thenAllowDedicatedDatabase() {
        assertThatCode(() -> validate(
                "jdbc:mysql://127.0.0.1:3306/cinewise_ticketing_concurrency_check?useSSL=false"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(
                "jdbc:mysql://mysql:3306/cinewise_ticketing_concurrency_check?useSSL=false"))
                .doesNotThrowAnyException();
    }

    @Test
    void givenRemoteHostOrWrongDatabase_whenValidate_thenRejectBeforeContextRefresh() {
        assertThatThrownBy(() -> validate(
                "jdbc:mysql://db.example.com:3306/cinewise_ticketing_concurrency_check"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validate("jdbc:mysql://127.0.0.1:3306/cinewise"))
                .isInstanceOf(IllegalStateException.class);
    }

    private void validate(String dataSourceUrl) {
        AdminOrderQueryMySqlIntegrationTest.DedicatedMySqlSafetyInitializer
                .validateDataSourceUrl(dataSourceUrl);
    }
}
