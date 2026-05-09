package com.secondhand.platform.modules.user.application;

import com.secondhand.platform.modules.user.UserProfileResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {
    private final JdbcTemplate jdbcTemplate;

    public UserApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserProfileResponse currentUserProfile(Long userId) {
        return loadProfile(userId);
    }

    public UserProfileResponse publicProfile(Long userId) {
        return loadProfile(userId);
    }

    private UserProfileResponse loadProfile(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        List<UserProfileResponse> rows = jdbcTemplate.query("""
                SELECT a.id, a.nickname, p.identity_status, p.video_identity_status, p.video_verified
                FROM user_account a
                LEFT JOIN user_profile p ON p.user_id = a.id
                WHERE a.id = ? AND a.status = 'ACTIVE'
                """, (rs, rowNum) -> {
                    String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
                    boolean approvedVideo = "APPROVED".equals(videoStatus) && rs.getBoolean("video_verified");
                    return new UserProfileResponse(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getString("identity_status") == null ? "UNVERIFIED" : rs.getString("identity_status"),
                            videoStatus,
                            approvedVideo
                    );
                }, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("user not found");
        }
        return rows.get(0);
    }
}
