package com.secondhand.platform.modules.location;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationApplicationService {
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final String KEY_PROVIDER = "location.provider";
    private static final String KEY_ENABLED = "location.enabled";
    private static final String KEY_DEFAULT_CITY = "location.default_city";
    private static final String KEY_DEFAULT_PROVINCE = "location.default_province";
    private static final String KEY_COORDINATE_TYPE = "location.coordinate_type";
    private static final String KEY_BAIDU_AK = "location.baidu_ak";
    private static final String CONFIG_GROUP = "location";

    private final BaiduReverseGeocodeClient baiduClient;
    private final JdbcTemplate jdbcTemplate;
    private final String envBaiduAk;

    public LocationApplicationService(BaiduReverseGeocodeClient baiduClient,
                                      @Value("${BAIDU_MAP_AK:}") String baiduAk,
                                      JdbcTemplate jdbcTemplate) {
        this.baiduClient = baiduClient;
        this.envBaiduAk = baiduAk == null ? "" : baiduAk.trim();
        this.jdbcTemplate = jdbcTemplate;
        ensureDefaults();
    }

    public synchronized LocationConfigResponse getConfig() {
        Map<String, String> values = loadConfig();
        String provider = values.getOrDefault(KEY_PROVIDER, GeoProvider.BAIDU.name());
        boolean enabled = Boolean.parseBoolean(values.getOrDefault(KEY_ENABLED, "true"));
        String defaultCity = values.getOrDefault(KEY_DEFAULT_CITY, "请选择城市");
        String defaultProvince = values.getOrDefault(KEY_DEFAULT_PROVINCE, "");
        String coordinateType = values.getOrDefault(KEY_COORDINATE_TYPE, "wgs84ll");
        return new LocationConfigResponse(
            provider,
            enabled,
            isConfigured(values, provider),
            defaultCity,
            defaultProvince,
            coordinateType,
            Instant.now().toString()
        );
    }

    @Transactional
    public synchronized LocationConfigResponse updateConfig(UpdateLocationConfigRequest request) {
        if (request == null) {
            return getConfig();
        }
        applyConfig(request.getProvider(), request.getEnabled(), request.getDefaultCity(),
            request.getDefaultProvince(), request.getCoordinateType(), null);
        return getConfig();
    }

    @Transactional
    public synchronized LocationConfigResponse adminUpdateConfig(AdminUpdateLocationConfigRequest request) {
        if (request == null) {
            return getConfig();
        }
        applyConfig(request.getProvider(), request.getEnabled(), request.getDefaultCity(),
            request.getDefaultProvince(), request.getCoordinateType(), request.getBaiduAk());
        return getConfig();
    }

    public ReverseGeocodeResponse reverse(ReverseGeocodeRequest request) {
        LocationConfigResponse config = getConfig();
        if (!config.enabled()) {
            return fallback(config, null, null);
        }
        BigDecimal latitude = request == null ? null : request.getLatitude();
        BigDecimal longitude = request == null ? null : request.getLongitude();
        validateCoordinate(latitude, longitude);
        String ak = currentBaiduAk();
        if (GeoProvider.BAIDU.name().equals(config.provider()) && !isBlank(ak)) {
            return baiduClient.reverse(ak, config.coordinateType(), latitude, longitude);
        }
        return fallback(config, latitude, longitude);
    }

    private void applyConfig(String providerValue, Boolean enabledValue, String defaultCityValue,
                             String defaultProvinceValue, String coordinateTypeValue, String baiduAkValue) {
        if (!isBlank(providerValue)) {
            upsert(KEY_PROVIDER, GeoProvider.valueOf(providerValue.trim().toUpperCase()).name(), "string", "定位服务商");
        }
        if (enabledValue != null) {
            upsert(KEY_ENABLED, Boolean.toString(enabledValue), "boolean", "定位开关");
        }
        if (!isBlank(defaultCityValue)) {
            upsert(KEY_DEFAULT_CITY, sanitizeName(defaultCityValue, "defaultCity"), "string", "默认城市");
        }
        if (defaultProvinceValue != null) {
            upsert(KEY_DEFAULT_PROVINCE, sanitizeName(defaultProvinceValue, "defaultProvince"), "string", "默认省份");
        }
        if (!isBlank(coordinateTypeValue)) {
            upsert(KEY_COORDINATE_TYPE, sanitizeCoordinateType(coordinateTypeValue), "string", "坐标类型");
        }
        if (!isBlank(baiduAkValue)) {
            upsert(KEY_BAIDU_AK, baiduAkValue.trim(), "secret", "百度地图 AK");
        }
    }

    private ReverseGeocodeResponse fallback(LocationConfigResponse config, BigDecimal latitude, BigDecimal longitude) {
        return new ReverseGeocodeResponse(config.provider(), config.defaultProvince(), config.defaultCity(), "", "", latitude, longitude, true);
    }

    private void validateCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("invalid latitude");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("invalid longitude");
        }
    }

    private String sanitizeName(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.length() > 40) {
            throw new IllegalArgumentException(field + " too long");
        }
        return trimmed;
    }

    private String sanitizeCoordinateType(String value) {
        String trimmed = value.trim().toLowerCase();
        if (!("wgs84ll".equals(trimmed) || "gcj02ll".equals(trimmed) || "bd09ll".equals(trimmed))) {
            throw new IllegalArgumentException("unsupported coordinate type");
        }
        return trimmed;
    }

    private boolean isConfigured(Map<String, String> values, String provider) {
        return !GeoProvider.BAIDU.name().equals(provider) || !isBlank(currentBaiduAk(values));
    }

    private String currentBaiduAk() {
        return currentBaiduAk(loadConfig());
    }

    private String currentBaiduAk(Map<String, String> values) {
        if (!isBlank(envBaiduAk)) {
            return envBaiduAk;
        }
        return values.getOrDefault(KEY_BAIDU_AK, "");
    }

    private void ensureDefaults() {
        upsertIfMissing(KEY_PROVIDER, GeoProvider.BAIDU.name(), "string", "定位服务商");
        upsertIfMissing(KEY_ENABLED, "true", "boolean", "定位开关");
        upsertIfMissing(KEY_DEFAULT_CITY, "请选择城市", "string", "默认城市");
        upsertIfMissing(KEY_DEFAULT_PROVINCE, "", "string", "默认省份");
        upsertIfMissing(KEY_COORDINATE_TYPE, "wgs84ll", "string", "坐标类型");
    }

    private Map<String, String> loadConfig() {
        return jdbcTemplate.query("""
                SELECT config_key, config_value
                FROM system_config
                WHERE config_group = ?
                """, (rs, rowNum) -> Map.entry(rs.getString("config_key"), rs.getString("config_value")), CONFIG_GROUP)
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
    }

    private void upsertIfMissing(String key, String value, String type, String remark) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, key);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO system_config (config_key, config_value, config_type, config_group, remark, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, key, value, type, CONFIG_GROUP, remark);
        }
    }

    private void upsert(String key, String value, String type, String remark) {
        int updated = jdbcTemplate.update("""
                UPDATE system_config
                SET config_value = ?, config_type = ?, config_group = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE config_key = ?
                """, value, type, CONFIG_GROUP, remark, key);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO system_config (config_key, config_value, config_type, config_group, remark, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, key, value, type, CONFIG_GROUP, remark);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
