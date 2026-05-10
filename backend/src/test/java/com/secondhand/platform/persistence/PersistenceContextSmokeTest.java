package com.secondhand.platform.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistenceContextSmokeTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceTestApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:persistence_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.sql.init.mode=always",
                    "spring.sql.init.schema-locations=classpath:db/schema.sql",
                    "spring.sql.init.data-locations=classpath:db/data.sql"
            );

    @Test
    void shouldCreatePersistenceCoreTablesFromStartupMigrations() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("jdbcTemplate"));
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            List<String> tables = jdbcTemplate.queryForList(
                    "select upper(table_name) from information_schema.tables where upper(table_schema) = 'PUBLIC'",
                    String.class
            );

            assertTrue(tables.contains("USER_ACCOUNT"), () -> "missing USER_ACCOUNT in " + tables);
            assertTrue(tables.contains("PRODUCT_ITEM"), () -> "missing PRODUCT_ITEM in " + tables);
            assertTrue(tables.contains("TRADE_ORDER"), () -> "missing TRADE_ORDER in " + tables);
            assertTrue(tables.contains("WALLET_ACCOUNT"), () -> "missing WALLET_ACCOUNT in " + tables);
            assertTrue(tables.contains("IM_MESSAGE"), () -> "missing IM_MESSAGE in " + tables);
            assertTrue(tables.contains("REPORT_RECORD"), () -> "missing REPORT_RECORD in " + tables);
            assertTrue(tables.contains("NOTIFICATION_RECORD"), () -> "missing NOTIFICATION_RECORD in " + tables);
            assertEquals("女装", jdbcTemplate.queryForObject(
                    "select config_value from system_config where config_key = 'platform.category.primary'",
                    String.class
            ));
            Long adminUserId = jdbcTemplate.queryForObject(
                    "select id from user_account where phone = '13800138000' and status = 'ACTIVE'",
                    Long.class
            );
            assertEquals(9, jdbcTemplate.queryForObject(
                    "select count(1) from admin_user_permission where user_id = ? and enabled = TRUE",
                    Integer.class,
                    adminUserId
            ));
        });
    }
}
