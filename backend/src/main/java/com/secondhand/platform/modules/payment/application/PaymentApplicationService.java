package com.secondhand.platform.modules.payment.application;

import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApplicationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WalletLedgerService walletLedgerService;
    private final JdbcTemplate jdbcTemplate;

    public PaymentApplicationService(WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate) {
        this.walletLedgerService = walletLedgerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RechargeResponse createRecharge(Long userId, CreateRechargeRequest request) {
        requireUserId(userId);
        validate(request);
        BigDecimal amount = money(request.getAmount());
        String channel = normalizeChannel(request.getChannel());
        String rechargeNo = uniqueRechargeNo();
        jdbcTemplate.update("""
                INSERT INTO payment_recharge_order (
                  recharge_no, user_id, amount, channel, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, rechargeNo, userId, amount, channel);
        return findRecharge(userId, rechargeNo);
    }

    @Transactional
    public RechargeResponse simulateRechargeSuccess(Long userId, String rechargeNo) {
        requireUserId(userId);
        String safeRechargeNo = requireText(rechargeNo, "rechargeNo required");
        RechargeResponse recharge = findRecharge(userId, safeRechargeNo);
        if (recharge == null) {
            throw new IllegalArgumentException("recharge order not found");
        }
        if ("PAID".equals(recharge.status())) {
            return recharge;
        }

        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setIdempotencyKey("payment:recharge:" + safeRechargeNo);
        command.setBizType("RECHARGE");
        command.setBizNo(safeRechargeNo);
        command.setBalanceType("RECHARGE");
        command.setAmount(recharge.amount());

        LedgerTransactionResponse ledger = walletLedgerService.credit(command);
        int updated = jdbcTemplate.update("""
                UPDATE payment_recharge_order
                SET status = 'PAID', ledger_no = ?, balance_before = ?, balance_after = ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE recharge_no = ? AND user_id = ? AND status = 'PENDING'
                """, ledger.ledgerNo(), ledger.balanceBefore(), ledger.balanceAfter(), safeRechargeNo, userId);
        if (updated == 0) {
            return findRecharge(userId, safeRechargeNo);
        }
        return findRecharge(userId, safeRechargeNo);
    }

    private RechargeResponse findRecharge(Long userId, String rechargeNo) {
        List<RechargeResponse> rows = jdbcTemplate.query("""
                SELECT user_id, recharge_no, amount, channel, status, ledger_no, balance_before, balance_after, created_at
                FROM payment_recharge_order
                WHERE user_id = ? AND recharge_no = ?
                """, (rs, rowNum) -> mapRecharge(rs), userId, rechargeNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RechargeResponse mapRecharge(ResultSet rs) throws SQLException {
        return new RechargeResponse(
                rs.getLong("user_id"),
                rs.getString("recharge_no"),
                rs.getBigDecimal("amount").setScale(2, RoundingMode.UNNECESSARY),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getString("ledger_no"),
                rs.getBigDecimal("balance_before"),
                rs.getBigDecimal("balance_after"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                false
        );
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
    }

    private void validate(CreateRechargeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("recharge request required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("recharge amount must be positive");
        }
        money(request.getAmount());
    }

    private BigDecimal money(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount scale must be <= 2", ex);
        }
    }

    private String normalizeChannel(String channel) {
        String safe = requireText(channel, "channel required").toUpperCase(Locale.ROOT);
        if (!"WECHAT".equals(safe) && !"ALIPAY".equals(safe)) {
            throw new IllegalArgumentException("unsupported recharge channel");
        }
        return safe;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String uniqueRechargeNo() {
        long now = System.currentTimeMillis();
        long random = SECURE_RANDOM.nextLong() & Long.MAX_VALUE;
        return String.format(Locale.ROOT, "RC-%d-%016X", now, random);
    }
}
