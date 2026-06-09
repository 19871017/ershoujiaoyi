package com.secondhand.platform.modules.user.application;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.modules.user.AccountSecurityResponse;
import com.secondhand.platform.modules.user.AdminUserDetailResponse;
import com.secondhand.platform.modules.user.AdminUserDetailResponse.AdminUserOpsSummary;
import com.secondhand.platform.modules.user.UpdateUserNoRequest;
import com.secondhand.platform.modules.user.UpdateUserProfileRequest;
import com.secondhand.platform.modules.user.UserProfileResponse;
import com.secondhand.platform.modules.user.UserRankingResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserApplicationService {
    private static final int MAX_SHOWCASE_PHOTOS = 6;
    private static final String VIDEO_IDENTITY_STORAGE_PREFIX = "/uploads/video-identity/";
    private static final Set<String> ALLOWED_GENDERS = Set.of("god", "goddess");
    private static final Set<String> VIDEO_IDENTITY_PUBLIC_ROLES = Set.of("SELLER", "BOTH");
    private static final Set<String> ADMIN_SEARCH_RESERVED_WORDS = Set.of("preview", "demo", "mock", "sample", "placeholder");
    private static final Set<String> USER_NO_RESERVED_WORDS = Set.of("preview", "demo", "mock", "sample", "placeholder", "admin", "system", "official", "root");
    private static final Pattern USER_NO_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{4,19}");

    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;
    private final NotificationApplicationService notificationApplicationService;

    public UserApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this(jdbcTemplate, mediaUploadTicketService, new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public UserApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService, NotificationApplicationService notificationApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.notificationApplicationService = notificationApplicationService;
        ensureShowcasePhotoTable();
    }

    public UserProfileResponse currentUserProfile(Long userId) {
        return loadProfile(userId, null, true);
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
        String gender = normalizeRequired(request.getGender(), 1, 16, "gender invalid").toLowerCase(Locale.ROOT);
        if (!ALLOWED_GENDERS.contains(gender)) {
            throw new IllegalArgumentException("gender invalid");
        }
        String city = normalizeOptional(request.getCity(), 24, "city invalid");
        String bio = normalizeOptional(request.getBio(), 60, "bio invalid");
        String avatarUrl = normalizeOptional(request.getAvatarUrl(), 512, "avatarUrl invalid");
        List<String> showcaseImageUrls = normalizeShowcaseImageUrls(request.getShowcaseImageUrls());
        ensureActiveUser(userId);
        if (avatarUrl != null) {
            avatarUrl = mediaUploadTicketService.requireUploadedStorageUrl(userId, "COMMUNITY_IMAGE", avatarUrl).storageUrl();
        }
        List<String> persistedShowcaseImageUrls = request.getShowcaseImageUrls() == null ? List.of() : loadPersistedShowcasePhotos(userId);
        for (String imageUrl : showcaseImageUrls) {
            if (!persistedShowcaseImageUrls.contains(imageUrl)) {
                mediaUploadTicketService.requireUploadedStorageUrl(userId, "COMMUNITY_IMAGE", imageUrl);
            }
        }
        jdbcTemplate.update("""
                UPDATE user_account
                SET nickname = ?, avatar_url = COALESCE(?, avatar_url), updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE'
                """, nickname, avatarUrl, userId);
        jdbcTemplate.update("""
                UPDATE user_profile
                SET gender = ?, city = ?, bio = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, gender, city, bio, userId);
        if (request.getShowcaseImageUrls() != null) {
            replaceShowcasePhotos(userId, showcaseImageUrls);
        }
        return currentUserProfile(userId);
    }

    public AccountSecurityResponse accountSecurity(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        List<AccountSecurityResponse> rows = jdbcTemplate.query("""
                SELECT id, phone
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> new AccountSecurityResponse(
                rs.getLong("id"),
                maskPhone(rs.getString("phone")),
                "--",
                List.of()
        ), userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("user not found");
        }
        return rows.get(0);
    }

    @Transactional
    public synchronized UserProfileResponse updateUserNo(Long userId, UpdateUserNoRequest request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        if (request == null) {
            throw new IllegalArgumentException("userNo request required");
        }
        ensureActiveUser(userId);
        String nextUserNo = normalizeUserNo(request.getUserNo());
        String currentUserNo = currentUserNo(userId);
        if (currentUserNo.equals(nextUserNo)) {
            return currentUserProfile(userId);
        }
        ensureUserNoChangeAvailable(userId);
        ensureUserNoAvailable(userId, nextUserNo);
        try {
            jdbcTemplate.update("""
                    UPDATE user_account
                    SET user_no = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """, nextUserNo, userId);
            jdbcTemplate.update("""
                    INSERT INTO user_no_change_log (user_id, old_user_no, new_user_no, created_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    """, userId, currentUserNo, nextUserNo);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("userNo unavailable", ex);
        }
        return currentUserProfile(userId);
    }

    @Transactional
    public UserProfileResponse followProfile(Long followerId, Long followedId) {
        validateFollowActors(followerId, followedId);
        boolean inserted = false;
        try {
            jdbcTemplate.update("""
                    INSERT INTO user_follow (follower_id, followed_id)
                    VALUES (?, ?)
                    """, followerId, followedId);
            inserted = true;
        } catch (DuplicateKeyException ignored) {
            // Idempotent follow: repeat requests keep the persisted relationship unchanged.
        }
        if (inserted) {
            notifyFollowCreated(followerId, followedId);
        }
        return publicProfile(followedId, followerId);
    }

    public UserProfileResponse unfollowProfile(Long followerId, Long followedId) {
        validateFollowActors(followerId, followedId);
        jdbcTemplate.update("DELETE FROM user_follow WHERE follower_id = ? AND followed_id = ?", followerId, followedId);
        return publicProfile(followedId, followerId);
    }

    public AdminUserDetailResponse adminUserDetail(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        List<AdminUserDetailResponse> rows = jdbcTemplate.query("""
                SELECT a.id, a.user_no, a.phone, a.nickname, a.status, a.created_at, a.updated_at,
                       p.main_role, p.city, p.bio, p.identity_status, p.video_identity_status, p.video_verified,
                       CASE WHEN video_identity.reason LIKE '/uploads/video-identity/%' THEN video_identity.reason ELSE NULL END AS video_identity_url
                FROM user_account a
                LEFT JOIN user_profile p ON p.user_id = a.id
                LEFT JOIN audit_record video_identity ON video_identity.id = (
                    SELECT MAX(ar.id)
                    FROM audit_record ar
                    WHERE ar.audit_type = 'VIDEO_IDENTITY'
                      AND ar.user_id = a.id
                      AND ar.target_id = CONCAT('', a.id)
                      AND ar.status = 'APPROVED'
                )
                WHERE a.id = ? AND a.status = 'ACTIVE'
                """, (rs, rowNum) -> {
                    String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
                    String mainRole = rs.getString("main_role") == null ? "BUYER" : rs.getString("main_role");
                    boolean approvedVideo = isPublicVideoVerified(rs.getLong("id"), mainRole, videoStatus, rs.getBoolean("video_verified"), rs.getString("video_identity_url"));
                    return new AdminUserDetailResponse(
                            rs.getLong("id"),
                            rs.getString("user_no"),
                            maskPhone(rs.getString("phone")),
                            rs.getString("nickname"),
                            rs.getString("status"),
                            mainRole,
                            rs.getString("city"),
                            rs.getString("bio"),
                            rs.getString("identity_status") == null ? "UNVERIFIED" : rs.getString("identity_status"),
                            videoStatus,
                            approvedVideo,
                            String.valueOf(rs.getTimestamp("created_at")),
                            String.valueOf(rs.getTimestamp("updated_at"))
                    );
                }, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("user not found");
        }
        AdminUserDetailResponse row = rows.get(0);
        return new AdminUserDetailResponse(
                row.getUserId(),
                row.getUserNo(),
                row.getMaskedPhone(),
                row.getNickname(),
                row.getStatus(),
                row.getMainRole(),
                row.getCity(),
                row.getBio(),
                row.getIdentityStatus(),
                row.getVideoIdentityStatus(),
                row.isVideoVerified(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                loadAdminUserOpsSummary(userId)
        );
    }

    public List<AdminUserDetailResponse> searchAdminUsers(String keyword, int limit) {
        String normalized = normalizeAdminSearchKeyword(keyword);
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit invalid");
        }
        String like = "%" + normalized + "%";
        List<AdminUserDetailResponse> rows = jdbcTemplate.query("""
                SELECT a.id, a.user_no, a.phone, a.nickname, a.status, a.created_at, a.updated_at,
                       p.main_role, p.city, p.bio, p.identity_status, p.video_identity_status, p.video_verified,
                       CASE WHEN video_identity.reason LIKE '/uploads/video-identity/%' THEN video_identity.reason ELSE NULL END AS video_identity_url
                FROM user_account a
                LEFT JOIN user_profile p ON p.user_id = a.id
                LEFT JOIN audit_record video_identity ON video_identity.id = (
                    SELECT MAX(ar.id)
                    FROM audit_record ar
                    WHERE ar.audit_type = 'VIDEO_IDENTITY'
                      AND ar.user_id = a.id
                      AND ar.target_id = CONCAT('', a.id)
                      AND ar.status = 'APPROVED'
                )
                WHERE a.status = 'ACTIVE'
                  AND (LOWER(a.nickname) LIKE LOWER(?) OR LOWER(a.user_no) LIKE LOWER(?) OR a.phone LIKE ?)
                ORDER BY a.id DESC
                LIMIT ?
                """, (rs, rowNum) -> {
                    String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
                    String mainRole = rs.getString("main_role") == null ? "BUYER" : rs.getString("main_role");
                    boolean approvedVideo = isPublicVideoVerified(rs.getLong("id"), mainRole, videoStatus, rs.getBoolean("video_verified"), rs.getString("video_identity_url"));
                    return new AdminUserDetailResponse(
                            rs.getLong("id"),
                            rs.getString("user_no"),
                            maskPhone(rs.getString("phone")),
                            rs.getString("nickname"),
                            rs.getString("status"),
                            mainRole,
                            rs.getString("city"),
                            rs.getString("bio"),
                            rs.getString("identity_status") == null ? "UNVERIFIED" : rs.getString("identity_status"),
                            videoStatus,
                            approvedVideo,
                            String.valueOf(rs.getTimestamp("created_at")),
                            String.valueOf(rs.getTimestamp("updated_at"))
                    );
                }, like, like, like, limit);
        return List.copyOf(rows);
    }

    private AdminUserOpsSummary loadAdminUserOpsSummary(Long userId) {
        long orderCount = count("""
                SELECT COUNT(*)
                FROM trade_order
                WHERE buyer_id = ? OR seller_id = ?
                """, userId, userId);
        long paidOrderCount = count("""
                SELECT COUNT(*)
                FROM trade_order
                WHERE (buyer_id = ? OR seller_id = ?)
                  AND order_status IN ('PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING')
                """, userId, userId);
        long afterSalesCount = count("""
                SELECT COUNT(DISTINCT ar.id)
                FROM after_sales_record ar
                LEFT JOIN trade_order o ON o.order_no = ar.order_no
                WHERE ar.applicant_id = ? OR o.buyer_id = ? OR o.seller_id = ?
                """, userId, userId, userId);
        long pendingAfterSalesCount = count("""
                SELECT COUNT(DISTINCT ar.id)
                FROM after_sales_record ar
                LEFT JOIN trade_order o ON o.order_no = ar.order_no
                WHERE (ar.applicant_id = ? OR o.buyer_id = ? OR o.seller_id = ?)
                  AND ar.after_sales_status = 'PENDING_REVIEW'
                """, userId, userId, userId);
        long reportCount = count("""
                SELECT COUNT(*)
                FROM report_record
                WHERE reporter_id = ? OR (UPPER(target_type) = 'USER' AND target_id = ?)
                """, userId, String.valueOf(userId));
        long pendingReportCount = count("""
                SELECT COUNT(*)
                FROM report_record
                WHERE (reporter_id = ? OR (UPPER(target_type) = 'USER' AND target_id = ?))
                  AND report_status = 'PENDING'
                """, userId, String.valueOf(userId));
        long withdrawalCount = count("""
                SELECT COUNT(*)
                FROM withdrawal_record
                WHERE user_id = ?
                """, userId);
        long pendingWithdrawalCount = count("""
                SELECT COUNT(*)
                FROM withdrawal_record
                WHERE user_id = ? AND status = 'PENDING'
                """, userId);
        long chatConversationCount = count("""
                SELECT COUNT(DISTINCT id)
                FROM im_conversation
                WHERE owner_user_id = ? OR peer_user_id = ?
                """, userId, userId);
        String lastOrderNo = findOne("""
                SELECT order_no
                FROM trade_order
                WHERE buyer_id = ? OR seller_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, String.class, userId, userId);
        String lastAfterSalesNo = findOne("""
                SELECT ar.after_sales_no
                FROM after_sales_record ar
                LEFT JOIN trade_order o ON o.order_no = ar.order_no
                WHERE ar.applicant_id = ? OR o.buyer_id = ? OR o.seller_id = ?
                ORDER BY ar.updated_at DESC, ar.id DESC
                LIMIT 1
                """, String.class, userId, userId, userId);
        String lastWithdrawalNo = findOne("""
                SELECT withdrawal_no
                FROM withdrawal_record
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, String.class, userId);
        Long lastChatConversationId = findOne("""
                SELECT id
                FROM im_conversation
                WHERE owner_user_id = ? OR peer_user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, Long.class, userId, userId);
        return new AdminUserOpsSummary(
                orderCount,
                paidOrderCount,
                afterSalesCount,
                pendingAfterSalesCount,
                reportCount,
                pendingReportCount,
                withdrawalCount,
                pendingWithdrawalCount,
                chatConversationCount,
                lastOrderNo,
                lastAfterSalesNo,
                lastWithdrawalNo,
                lastChatConversationId
        );
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private <T> T findOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.queryForList(sql, type, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UserRankingResponse> listRankings(String gender, String period, int limit, Long viewerId) {
        String normalizedGender = normalizeRequired(gender, 1, 16, "gender invalid").toLowerCase(Locale.ROOT);
        if (!ALLOWED_GENDERS.contains(normalizedGender)) {
            throw new IllegalArgumentException("gender invalid");
        }
        String normalizedPeriod = normalizeRequired(period, 1, 16, "period invalid").toLowerCase(Locale.ROOT);
        if (!Set.of("day", "week", "all").contains(normalizedPeriod)) {
            throw new IllegalArgumentException("period invalid");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit invalid");
        }
        boolean hasViewer = viewerId != null && viewerId > 0;
        LocalDateTime periodCutoff = switch (normalizedPeriod) {
            case "day" -> LocalDateTime.now().minusDays(1);
            case "week" -> LocalDateTime.now().minusDays(7);
            default -> null;
        };
        String giftPeriodFilter = periodCutoff == null ? "" : " AND created_at >= ?";
        String orderPeriodFilter = periodCutoff == null ? "" : " AND paid_at >= ?";
        String scoreSubquery = "goddess".equals(normalizedGender)
                ? String.format("""
                    SELECT receiver_id AS owner_user_id, FLOOR(COALESCE(SUM(total_amount), 0)) AS gift_score
                    FROM gift_order
                    WHERE status = 'SUCCESS'%s
                    GROUP BY receiver_id
                """, giftPeriodFilter)
                : String.format("""
                    SELECT owner_user_id, FLOOR(COALESCE(SUM(amount), 0)) AS gift_score
                    FROM (
                        SELECT sender_id AS owner_user_id, total_amount AS amount
                        FROM gift_order
                        WHERE status = 'SUCCESS'%s
                        UNION ALL
                        SELECT buyer_id AS owner_user_id, amount
                        FROM trade_order
                        WHERE order_status IN ('PAID', 'SHIPPED', 'COMPLETED')%s
                    ) score_events
                    GROUP BY owner_user_id
                """, giftPeriodFilter, orderPeriodFilter);
        String rankingSql = String.format("""
                SELECT a.id,
                       a.nickname,
                       a.avatar_url,
                       p.gender,
                       p.city,
                       p.bio,
                       p.main_role,
                       p.video_identity_status,
                       p.video_verified,
                       video_identity.reason AS video_identity_url,
                       COUNT(DISTINCT f.id) AS follower_count,
                       COALESCE(g.gift_score, 0) AS gift_score,
                       CASE WHEN ? = TRUE AND EXISTS (
                           SELECT 1 FROM user_follow vf WHERE vf.follower_id = ? AND vf.followed_id = a.id
                       ) THEN TRUE ELSE FALSE END AS followed_by_me
                FROM user_account a
                JOIN user_profile p ON p.user_id = a.id
                LEFT JOIN user_follow f ON f.followed_id = a.id
                LEFT JOIN audit_record video_identity ON video_identity.id = (
                    SELECT MAX(ar.id)
                    FROM audit_record ar
                    WHERE ar.audit_type = 'VIDEO_IDENTITY'
                      AND ar.user_id = a.id
                      AND ar.target_id = CONCAT('', a.id)
                      AND ar.status = 'APPROVED'
                )
                LEFT JOIN (
                    %s
                ) g ON g.owner_user_id = a.id
                WHERE a.status = 'ACTIVE' AND LOWER(p.gender) = ?
                GROUP BY a.id, a.nickname, a.avatar_url, p.gender, p.city, p.bio, p.main_role, p.video_identity_status, p.video_verified, video_identity.reason, g.gift_score
                ORDER BY gift_score DESC, a.id ASC
                LIMIT ?
                """, scoreSubquery);
        List<Object> rankingParams = new ArrayList<>();
        rankingParams.add(hasViewer);
        rankingParams.add(hasViewer ? viewerId : -1L);
        if (periodCutoff != null) {
            rankingParams.add(periodCutoff);
            if ("god".equals(normalizedGender)) rankingParams.add(periodCutoff);
        }
        rankingParams.add(normalizedGender);
        rankingParams.add(limit);
        List<UserRankingResponse> rows = jdbcTemplate.query(rankingSql, (rs, rowNum) -> {
            String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
            String mainRole = rs.getString("main_role") == null ? "BUYER" : rs.getString("main_role");
            String rawVideoIdentityUrl = rs.getString("video_identity_url");
            boolean approvedVideo = "APPROVED".equalsIgnoreCase(videoStatus)
                    && rs.getBoolean("video_verified")
                    && VIDEO_IDENTITY_PUBLIC_ROLES.contains(mainRole.toUpperCase(Locale.ROOT))
                    && isCanonicalVideoIdentityUrl(rawVideoIdentityUrl)
                    && isUploadedVideoIdentityUrl(rs.getLong("id"), rawVideoIdentityUrl);
            String responseVideoStatus = approvedVideo ? "APPROVED" : "UNVERIFIED";
            return new UserRankingResponse(
                    rs.getLong("id"),
                    rowNum + 1,
                    rs.getString("nickname"),
                    rs.getString("avatar_url"),
                    rs.getString("gender"),
                    rs.getString("city"),
                    rs.getString("bio"),
                    mainRole,
                    responseVideoStatus,
                    approvedVideo,
                    rs.getInt("follower_count"),
                    rs.getInt("gift_score"),
                    rs.getInt("gift_score"),
                    rs.getInt("gift_score"),
                    rs.getInt("gift_score"),
                    rs.getBoolean("followed_by_me")
            );
        }, rankingParams.toArray());
        return List.copyOf(rows);
    }

    private UserProfileResponse loadProfile(Long userId) {
        return loadProfile(userId, null, false);
    }

    private UserProfileResponse loadProfile(Long userId, Long viewerId) {
        return loadProfile(userId, viewerId, false);
    }

    private UserProfileResponse loadProfile(Long userId, Long viewerId, boolean exposePendingVideoIdentityUrl) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        List<UserProfileResponse> rows = jdbcTemplate.query("""
                SELECT a.id, a.user_no, a.nickname, a.avatar_url, p.identity_status, p.main_role, p.gender, p.city, p.bio, p.video_identity_status, p.video_verified,
                       COALESCE(followers.follower_count, 0) AS follower_count,
                       COALESCE(following.following_count, 0) AS following_count,
                       COALESCE(received_gifts.seller_charm_score, 0) AS seller_charm_score,
                       FLOOR(COALESCE(sent_gifts.sent_gift_amount, 0) + COALESCE(paid_orders.paid_order_amount, 0)) AS buyer_power_score,
                       CASE WHEN video_identity.reason LIKE '/uploads/video-identity/%' THEN video_identity.reason ELSE NULL END AS video_identity_url
                FROM user_account a
                LEFT JOIN user_profile p ON p.user_id = a.id
                LEFT JOIN (
                    SELECT followed_id, COUNT(*) AS follower_count
                    FROM user_follow
                    GROUP BY followed_id
                ) followers ON followers.followed_id = a.id
                LEFT JOIN (
                    SELECT follower_id, COUNT(*) AS following_count
                    FROM user_follow
                    GROUP BY follower_id
                ) following ON following.follower_id = a.id
                LEFT JOIN (
                    SELECT receiver_id, FLOOR(COALESCE(SUM(total_amount), 0)) AS seller_charm_score
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY receiver_id
                ) received_gifts ON received_gifts.receiver_id = a.id
                LEFT JOIN (
                    SELECT sender_id, COALESCE(SUM(total_amount), 0) AS sent_gift_amount
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY sender_id
                ) sent_gifts ON sent_gifts.sender_id = a.id
                LEFT JOIN (
                    SELECT buyer_id, COALESCE(SUM(amount), 0) AS paid_order_amount
                    FROM trade_order
                    WHERE order_status IN ('PAID', 'SHIPPED', 'COMPLETED')
                    GROUP BY buyer_id
                ) paid_orders ON paid_orders.buyer_id = a.id
                LEFT JOIN audit_record video_identity ON video_identity.id = (
                    SELECT MAX(ar.id)
                    FROM audit_record ar
                    WHERE ar.audit_type = 'VIDEO_IDENTITY'
                      AND ar.user_id = a.id
                      AND ar.target_id = CONCAT('', a.id)
                      AND ar.status = p.video_identity_status
                      AND ar.status IN ('APPROVED', 'PENDING')
                )
                WHERE a.id = ? AND a.status = 'ACTIVE'
                """, (rs, rowNum) -> {
                    String videoStatus = rs.getString("video_identity_status") == null ? "UNVERIFIED" : rs.getString("video_identity_status");
                    String mainRole = rs.getString("main_role") == null ? "BUYER" : rs.getString("main_role");
                    String rawVideoIdentityUrl = rs.getString("video_identity_url");
                    String storedVideoIdentityUrl = isUploadedVideoIdentityUrl(rs.getLong("id"), rawVideoIdentityUrl) ? rawVideoIdentityUrl : null;
                    boolean approvedVideo = "APPROVED".equals(videoStatus) && rs.getBoolean("video_verified");
                    boolean sellerApprovedVideo = approvedVideo && VIDEO_IDENTITY_PUBLIC_ROLES.contains(mainRole.toUpperCase(Locale.ROOT)) && storedVideoIdentityUrl != null;
                    boolean pendingOwnVideo = exposePendingVideoIdentityUrl && "PENDING".equals(videoStatus) && storedVideoIdentityUrl != null;
                    boolean rejectedOwnVideo = exposePendingVideoIdentityUrl && "REJECTED".equals(videoStatus);
                    String responseVideoStatus = sellerApprovedVideo || pendingOwnVideo || rejectedOwnVideo ? videoStatus : "UNVERIFIED";
                    String videoIdentityUrl = sellerApprovedVideo || pendingOwnVideo ? storedVideoIdentityUrl : null;
                    return new UserProfileResponse(
                            rs.getLong("id"),
                            rs.getString("user_no"),
                            rs.getString("nickname"),
                            rs.getString("avatar_url"),
                            mainRole,
                            rs.getString("gender"),
                            rs.getString("city"),
                            rs.getString("bio"),
                            rs.getString("identity_status") == null ? "UNVERIFIED" : rs.getString("identity_status"),
                            responseVideoStatus,
                            sellerApprovedVideo,
                            videoIdentityUrl,
                            pendingOwnVideo,
                            loadShowcasePhotos(rs.getLong("id"), sellerApprovedVideo),
                            viewerId != null && isFollowedBy(viewerId, rs.getLong("id")),
                            rs.getInt("follower_count"),
                            rs.getInt("following_count"),
                            rs.getInt("seller_charm_score"),
                            rs.getInt("buyer_power_score")
                    );
                }, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("user not found");
        }
        return rows.get(0);
    }

    private boolean isCanonicalVideoIdentityUrl(String url) {
        if (url == null || !url.startsWith(VIDEO_IDENTITY_STORAGE_PREFIX)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        String relativePath = url.substring(VIDEO_IDENTITY_STORAGE_PREFIX.length());
        if (relativePath.isBlank()
                || url.startsWith("local://")
                || url.startsWith("blob:")
                || url.startsWith("data:")
                || lower.contains("placeholder")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || url.contains("\\")
                || url.contains("..")
                || url.contains("//")) {
            return false;
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isUploadedVideoIdentityUrl(Long userId, String url) {
        if (userId == null || !isCanonicalVideoIdentityUrl(url)) {
            return false;
        }
        try {
            mediaUploadTicketService.requireUploadedVideoIdentityMedia(userId, url);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isPublicVideoVerified(Long userId, String mainRole, String videoStatus, boolean rawVideoVerified, String rawVideoIdentityUrl) {
        String role = mainRole == null ? "BUYER" : mainRole.toUpperCase(Locale.ROOT);
        return "APPROVED".equals(videoStatus)
                && rawVideoVerified
                && VIDEO_IDENTITY_PUBLIC_ROLES.contains(role)
                && isUploadedVideoIdentityUrl(userId, rawVideoIdentityUrl);
    }

    private void ensureShowcasePhotoTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_showcase_photo (
                  user_id BIGINT NOT NULL,
                  image_url VARCHAR(512) NOT NULL,
                  sort_order INT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (user_id, image_url)
                )
                """);
    }

    private List<String> normalizeShowcaseImageUrls(List<String> urls) {
        if (urls == null) {
            return List.of();
        }
        List<String> normalized = urls.stream()
                .map(url -> normalizeOptional(url, 512, "showcaseImageUrl invalid"))
                .filter(url -> url != null)
                .distinct()
                .toList();
        if (normalized.size() > MAX_SHOWCASE_PHOTOS) {
            throw new IllegalArgumentException("showcaseImageUrls too many");
        }
        return normalized;
    }

    private void replaceShowcasePhotos(Long userId, List<String> urls) {
        jdbcTemplate.update("DELETE FROM user_showcase_photo WHERE user_id = ?", userId);
        for (int i = 0; i < urls.size(); i++) {
            jdbcTemplate.update("""
                    INSERT INTO user_showcase_photo (user_id, image_url, sort_order, created_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    """, userId, urls.get(i), i);
        }
    }

    private List<String> loadShowcasePhotos(Long userId, boolean approvedVideo) {
        if (!approvedVideo) {
            return List.of();
        }
        return loadPersistedShowcasePhotos(userId);
    }

    private List<String> loadPersistedShowcasePhotos(Long userId) {
        return jdbcTemplate.query("""
                SELECT image_url
                FROM user_showcase_photo
                WHERE user_id = ?
                ORDER BY sort_order ASC, created_at ASC
                LIMIT ?
                """, (rs, rowNum) -> rs.getString("image_url"), userId, MAX_SHOWCASE_PHOTOS);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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

    private String normalizeAdminSearchKeyword(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 32) {
            throw new IllegalArgumentException("keyword invalid");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAnyReservedWord(lower, ADMIN_SEARCH_RESERVED_WORDS)) {
            throw new IllegalArgumentException("keyword invalid");
        }
        return normalized;
    }

    private String normalizeUserNo(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!USER_NO_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("userNo invalid");
        }
        if (containsAnyReservedWord(normalized.toLowerCase(Locale.ROOT), USER_NO_RESERVED_WORDS)) {
            throw new IllegalArgumentException("userNo invalid");
        }
        return normalized;
    }

    private boolean containsAnyReservedWord(String lower, Set<String> reservedWords) {
        for (String reservedWord : reservedWords) {
            if (lower.contains(reservedWord)) {
                return true;
            }
        }
        return false;
    }

    private String currentUserNo(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_no FROM user_account WHERE id = ? AND status = 'ACTIVE'",
                String.class,
                userId
        );
    }

    private void ensureUserNoChangeAvailable(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_no_change_log WHERE user_id = ?",
                Integer.class,
                userId
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("userNo change already used");
        }
    }

    private void ensureUserNoAvailable(Long userId, String userNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE LOWER(user_no) = LOWER(?) AND id <> ?",
                Integer.class,
                userNo,
                userId
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("userNo unavailable");
        }
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

    private void notifyFollowCreated(Long followerId, Long followedId) {
        notificationApplicationService.createNotification(
                followedId,
                "FOLLOW",
                "你有新的关注",
                displayUserName(followerId) + " 关注了你",
                "/pages/user/public-profile/index?userId=" + followerId
        );
    }

    private String displayUserName(Long userId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(nickname, ''), NULLIF(user_no, ''), CONCAT('用户', id)) AS display_name
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getString("display_name"), userId);
        return rows.isEmpty() ? "用户" + userId : rows.get(0);
    }
}
