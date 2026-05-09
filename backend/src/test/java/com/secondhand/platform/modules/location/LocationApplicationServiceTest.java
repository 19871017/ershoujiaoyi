package com.secondhand.platform.modules.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class LocationApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private LocationApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new LocationApplicationService(new BaiduReverseGeocodeClient(), "", jdbcTemplate);
    }

    @Test
    void getConfigShouldReturnDefaultFromDatabaseAndNeverExposeAk() {
        LocationConfigResponse config = service.getConfig();

        assertEquals("BAIDU", config.provider());
        assertTrue(config.enabled());
        assertFalse(config.configured());
        assertEquals("请选择城市", config.defaultCity());
        assertEquals("wgs84ll", config.coordinateType());
    }

    @Test
    void adminUpdateShouldPersistConfigAndAkAcrossServiceRecreation() {
        AdminUpdateLocationConfigRequest request = new AdminUpdateLocationConfigRequest();
        request.setProvider("baidu");
        request.setEnabled(false);
        request.setDefaultCity("广州");
        request.setDefaultProvince("广东");
        request.setCoordinateType("gcj02ll");
        request.setBaiduAk("SECRET-AK-SHOULD-NOT-LEAK");

        LocationConfigResponse updated = service.adminUpdateConfig(request);
        LocationApplicationService reloaded = new LocationApplicationService(new BaiduReverseGeocodeClient(), "", jdbcTemplate);
        LocationConfigResponse config = reloaded.getConfig();

        assertFalse(updated.enabled());
        assertTrue(updated.configured());
        assertEquals("广州", config.defaultCity());
        assertEquals("广东", config.defaultProvince());
        assertEquals("gcj02ll", config.coordinateType());
        assertTrue(config.configured());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_value = ?", Integer.class, "[REDACTED]"));
    }

    @Test
    void publicUpdateShouldPersistNonSecretConfigWithoutClearingAk() {
        AdminUpdateLocationConfigRequest admin = new AdminUpdateLocationConfigRequest();
        admin.setBaiduAk("SECRET-AK-SHOULD-STAY");
        service.adminUpdateConfig(admin);

        UpdateLocationConfigRequest request = new UpdateLocationConfigRequest();
        request.setDefaultCity("深圳");
        request.setCoordinateType("bd09ll");
        LocationConfigResponse updated = service.updateConfig(request);
        LocationApplicationService reloaded = new LocationApplicationService(new BaiduReverseGeocodeClient(), "", jdbcTemplate);
        LocationConfigResponse config = reloaded.getConfig();

        assertEquals("深圳", updated.defaultCity());
        assertEquals("深圳", config.defaultCity());
        assertEquals("bd09ll", config.coordinateType());
        assertTrue(config.configured());
    }

    @Test
    void updateShouldRejectInvalidProviderAndCoordinateType() {
        UpdateLocationConfigRequest badProvider = new UpdateLocationConfigRequest();
        badProvider.setProvider("unknown");
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(badProvider));

        UpdateLocationConfigRequest badCoordinate = new UpdateLocationConfigRequest();
        badCoordinate.setCoordinateType("gps84");
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(badCoordinate));
    }
}
