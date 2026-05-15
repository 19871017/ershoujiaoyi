package com.secondhand.platform.modules.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.auth.LoginRequest;
import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.modules.user.UserRankingResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class UserRankingApplicationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private UserApplicationService service;
    private AuthApplicationService auth;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new UserApplicationService(jdbcTemplate);
        auth = new AuthApplicationService(jdbcTemplate);
    }

    @Test
    void listRankingShouldReturnBackendProfilesSortedByGiftScoreWithViewerState() {
        Long viewerId = loginUser("13800139001");
        Long firstId = loginUser("13800139002");
        Long secondId = loginUser("13800139003");
        Long thirdId = loginUser("13800139004");
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "真实榜一", firstId);
        jdbcTemplate.update("UPDATE user_account SET nickname = ? WHERE id = ?", "真实榜二", secondId);
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, city = ?, bio = ?, main_role = ? WHERE user_id = ?", "goddess", "杭州", "后端资料一", "SELLER", firstId);
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, city = ?, bio = ?, main_role = ? WHERE user_id = ?", "goddess", "上海", "后端资料二", "SELLER", secondId);
        jdbcTemplate.update("UPDATE user_profile SET gender = ?, city = ?, bio = ? WHERE user_id = ?", "god", "杭州", "不在女神榜", thirdId);
        service.followProfile(viewerId, firstId);
        service.followProfile(thirdId, firstId);
        service.followProfile(viewerId, secondId);
        insertGiftOrder(viewerId, firstId, "GO-RANK-1", "88.90");
        insertGiftOrder(viewerId, secondId, "GO-RANK-2", "12.00");

        List<UserRankingResponse> rankings = service.listRankings("goddess", 10, viewerId);

        assertEquals(2, rankings.size());
        assertEquals(firstId, rankings.get(0).getUserId());
        assertEquals(1, rankings.get(0).getRank());
        assertEquals("真实榜一", rankings.get(0).getNickname());
        assertEquals("杭州", rankings.get(0).getCity());
        assertEquals("后端资料一", rankings.get(0).getBio());
        assertEquals(2, rankings.get(0).getFollowerCount());
        assertEquals(88, rankings.get(0).getGiftScore());
        assertEquals(88, rankings.get(0).getPopularityScore());
        assertEquals(true, rankings.get(0).isFollowedByMe());
        assertEquals(secondId, rankings.get(1).getUserId());
        assertEquals(2, rankings.get(1).getRank());
        assertEquals(1, rankings.get(1).getFollowerCount());
        assertEquals(12, rankings.get(1).getGiftScore());
    }

    @Test
    void listRankingShouldRejectInvalidParamsAndNeverReturnPreviewUsers() {
        assertThrows(IllegalArgumentException.class, () -> service.listRankings("preview", 10, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.listRankings("goddess", 0, 1L));

        Long userId = loginUser("13800139005");
        jdbcTemplate.update("UPDATE user_profile SET gender = ? WHERE user_id = ?", "goddess", userId);
        List<UserRankingResponse> rankings = service.listRankings("goddess", 10, null);

        assertEquals(1, rankings.size());
        assertFalse(rankings.get(0).getNickname().contains("预览"));
        assertEquals(false, rankings.get(0).isFollowedByMe());
    }

    private void insertGiftOrder(Long senderId, Long receiverId, String orderNo, String amount) {
        jdbcTemplate.update("""
                INSERT INTO gift_order (gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity, total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderNo, "IDEM-" + orderNo, senderId, receiverId, 1L, "ROSE", 1, amount, "0.00", amount, "DL-" + orderNo, "CL-" + orderNo, "SUCCESS");
    }

    private Long loginUser(String mobile) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword("pass-123456");
        auth.register(request, "test-" + mobile);
        return jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, mobile);
    }
}
