package com.secondhand.platform.modules.address.application;

import com.secondhand.platform.modules.address.AddressRequest;
import com.secondhand.platform.modules.address.AddressResponse;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressApplicationService {
    private final JdbcTemplate jdbcTemplate;

    public AddressApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AddressResponse> listAddresses(Long userId) {
        long safeUserId = requireUserId(userId);
        return jdbcTemplate.query("""
                SELECT * FROM user_address
                WHERE user_id = ?
                ORDER BY is_default DESC, updated_at DESC, id DESC
                """, (rs, rowNum) -> new AddressResponse(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("name"),
                maskMobile(rs.getString("mobile")),
                rs.getString("province_city"),
                rs.getString("detail"),
                rs.getBoolean("is_default"),
                timeText(rs.getTimestamp("created_at")),
                timeText(rs.getTimestamp("updated_at"))
        ), safeUserId);
    }

    public AddressResponse getAddress(Long userId, Long addressId) {
        long safeUserId = requireUserId(userId);
        long safeAddressId = requireAddressId(addressId);
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT * FROM user_address WHERE id = ? AND user_id = ?
                    """, (rs, rowNum) -> new AddressResponse(
                    rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"), maskMobile(rs.getString("mobile")),
                    rs.getString("province_city"), rs.getString("detail"), rs.getBoolean("is_default"),
                    timeText(rs.getTimestamp("created_at")), timeText(rs.getTimestamp("updated_at"))
            ), safeAddressId, safeUserId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("address not found");
        }
    }

    @Transactional
    public AddressResponse saveAddress(Long userId, AddressRequest request) {
        long safeUserId = requireUserId(userId);
        if (request == null) throw new IllegalArgumentException("address required");
        String name = requireLength(request.getName(), 1, 24, "address name invalid");
        String mobile = normalizeMobile(request.getMobile());
        String provinceCity = requireLength(request.getProvinceCity(), 2, 80, "address region invalid");
        String detail = requireLength(request.getDetail(), 2, 160, "address detail invalid");
        boolean defaultAddress = Boolean.TRUE.equals(request.getIsDefault());
        Long addressId = request.getAddressId();
        if (addressId == null || addressId == 0) {
            if (defaultAddress) clearDefaults(safeUserId);
            jdbcTemplate.update("""
                    INSERT INTO user_address (user_id, name, mobile, province_city, detail, is_default, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, safeUserId, name, mobile, provinceCity, detail, defaultAddress);
            Long newId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM user_address WHERE user_id = ?", Long.class, safeUserId);
            return getAddress(safeUserId, newId);
        }
        long safeAddressId = requireAddressId(addressId);
        ensureOwner(safeUserId, safeAddressId);
        if (defaultAddress) clearDefaults(safeUserId);
        jdbcTemplate.update("""
                UPDATE user_address
                SET name = ?, mobile = ?, province_city = ?, detail = ?, is_default = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ?
                """, name, mobile, provinceCity, detail, defaultAddress, safeAddressId, safeUserId);
        return getAddress(safeUserId, safeAddressId);
    }

    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        long safeUserId = requireUserId(userId);
        long safeAddressId = requireAddressId(addressId);
        ensureOwner(safeUserId, safeAddressId);
        clearDefaults(safeUserId);
        jdbcTemplate.update("UPDATE user_address SET is_default = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?", safeAddressId, safeUserId);
        return getAddress(safeUserId, safeAddressId);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        long safeUserId = requireUserId(userId);
        long safeAddressId = requireAddressId(addressId);
        ensureOwner(safeUserId, safeAddressId);
        jdbcTemplate.update("DELETE FROM user_address WHERE id = ? AND user_id = ?", safeAddressId, safeUserId);
    }

    private void ensureOwner(long userId, long addressId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_address WHERE id = ? AND user_id = ?", Integer.class, addressId, userId);
        if (count == null || count == 0) throw new IllegalArgumentException("address not found");
    }

    private void clearDefaults(long userId) {
        jdbcTemplate.update("UPDATE user_address SET is_default = FALSE, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", userId);
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId required");
        return userId;
    }

    private long requireAddressId(Long addressId) {
        if (addressId == null || addressId <= 0) throw new IllegalArgumentException("addressId required");
        return addressId;
    }

    private String requireLength(String value, int min, int max, String message) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() < min || safe.length() > max || containsForbiddenMarker(safe)) throw new IllegalArgumentException(message);
        return safe;
    }

    private String normalizeMobile(String mobile) {
        String safe = mobile == null ? "" : mobile.trim();
        if (!safe.matches("^1\\d{10}$")) throw new IllegalArgumentException("address mobile invalid");
        return safe;
    }

    private boolean containsForbiddenMarker(String value) {
        String lower = value.toLowerCase();
        return lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder");
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return "";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String timeText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }
}
