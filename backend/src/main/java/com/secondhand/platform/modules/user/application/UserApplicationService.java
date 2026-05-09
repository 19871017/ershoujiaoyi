package com.secondhand.platform.modules.user.application;

import com.secondhand.platform.modules.user.UpdateUserProfileRequest;
import com.secondhand.platform.modules.user.UserProfileResponse;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {
    private static final Set<String> ALLOWED_ROLES = Set.of("BUYER", "SELLER", "BOTH");
    private final JdbcTemplate jdbcTemplate;

    public UserApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserProfileResponse currentUserProfile(Long userId) {
        return loadProfile(userId);
    }

    public UserProfileResponse publicProfile(Long userId) {
        return loadProfile(userId, null);
    }

    public UserProfileResponse publicProfile(Long userId, Long viewerId) {
        return loadProfile(userId, viewerId);
    }

    public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        if (request == null) {
            throw new IllegalArgumentException("profile request required");
        }
        String nickname = normalizeRequired(request.getNickname(), 1, 16, "nickname invalid");
        String mainRole = normalizeRequired(request.getMainRole(), 1, 16, "mainRole invalid");
        if (!ALLOWED_ROLES.contains(mainRole)) {
            throw new IllegalArgumentException("mainRole invalid");
        }
        String city = normalizeOptional(request.getCity(), 24, "city invalid");
        String bio = normalizeOptional(request.getBio(), 60, "bio invalid");
        ensureActiveUser(userId);
        jdbcTemplate.update("""
                UPDATE user_account SET nickname = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'ACTIVE'
                """, nickname, userId);
        jdbcTemplate.update("""
                UPDATE user_profile SET main_role = ?, city = ?, bio = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?
                """, mainRole, city, bio, userId);
        return currentUserProfile(userId);
    }

    public UserProfileResponse followProfile(Long followerId, Long followedId) {
        validateFollowActors(followerId, followedId);
        try {
            jdbcTemplate.update("""
                    INSERT INTO user_follow (follower_id, followed_id)
                    VALUES (?, ?)
                    """, followerId, followedId);
        } catch (DuplicateKeyException ignored) {
            // Idempotent follow: repeat requests keep the persisted relationship unchanged.
        }
        return publicProfile(followedId, followerId);
    }

    public UserProfileResponse unfollowProfile(Long followerId, Long followedId) {
        validateFollowActors(followerId, followedId);
        jdbcTemplate.update("DELETE FROM user_follow WHERE follower_id = ? AND followed_id = ?", followerId, followedId);
        return publicProfile(followedId, followerId);
    }

    private UserProfileResponse loadProfile(Long userId) {
        return loadProfile(userId, null);
    }

    private UserProfileResponse loadProfile(Long userId, Long viewerId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        List<UserProfileResponse> rows = jdbcTemplate.query("""
                SELECT a.id, a.nickname, p.identity_status, p.main_role, p.city, p.bio, p.video_identity_status, p.video_verified
                FROM user_account a
                LEFT JOIN user_profile p ON p.user_id = a.id
                WHERE a.id = ? AND a.status = 'ACTIVE'
                """, (rs, rowNum) -> {
                    String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
                    boolean approvedVideo = "APPROVED".equals(videoStatus) && rs.getBoolean("video_verified");
                    return new UserProfileResponse(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getString("main_role") == null ? "BUYER" : rs.getString("main_role"),
                            rs.getString("city"),
                            rs.getString("bio"),
                            videoStatus,
                            approvedVideo,
                            viewerId != null && isFollowedBy(viewerId, rs.getLong("id"))
                    );
                }, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("user not found");
        }
        return rows.get(0);
    }

    private String normalizeRequired(String value, int minLength, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minLength || normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String message) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private void validateFollowActors(Long followerId, Long followedId) {
        if (followerId == null || followerId <= 0 || followedId == null || followedId <= 0) {
            throw new IllegalArgumentException("valid follower and followed users required");
        }
        if (followerId.equals(followedId)) {
            throw new IllegalArgumentException("cannot follow self");
        }
        ensureActiveUser(followerId);
        ensureActiveUser(followedId);
    }

    private void ensureActiveUser(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("user not found");
        }
    }

    private boolean isFollowedBy(Long followerId, Long followedId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE follower_id = ? AND followed_id = ?",
                Integer.class,
                followerId,
                followedId
        );
        return count != null && count > 0;
    }
}
