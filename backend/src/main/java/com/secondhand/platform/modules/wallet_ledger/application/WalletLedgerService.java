package com.secondhand.platform.modules.wallet_ledger.application;

import com.secondhand.platform.modules.wallet_ledger.CreateWithdrawalRequest;
import com.secondhand.platform.modules.wallet_ledger.PayoutAccountRequest;
import com.secondhand.platform.modules.wallet_ledger.PayoutAccountResponse;
import com.secondhand.platform.modules.wallet_ledger.WalletBalanceResponse;
import com.secondhand.platform.modules.wallet_ledger.WalletLedgerItemResponse;
import com.secondhand.platform.modules.wallet_ledger.WithdrawalResponse;
import com.secondhand.platform.modules.wallet_ledger.domain.WalletAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletLedgerService {
    private static final int MONEY_SCALE = 2;
    private static final String DEFAULT_BALANCE_TYPE = "RECHARGE";
    private static final String BALANCE_TYPE_RECHARGE = "RECHARGE";
    private static final String BALANCE_TYPE_INCOME = "INCOME";
    private static final String BALANCE_TYPE_WITHDRAWABLE = "WITHDRAWABLE";

    private final JdbcTemplate jdbcTemplate;

    public WalletLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long bindPayoutAccount(Long userId, PayoutAccountRequest request) {
        validateUserId(userId, "payout account");
        if (request == null) {
            throw new IllegalArgumentException("payout account request required");
        }
        String paymentMethod = requireText(request.getPaymentMethod(), "payout account paymentMethod required").toUpperCase(Locale.ROOT);
        String accountName = requireText(request.getAccountName(), "payout account accountName required");
        String accountNo = requireText(request.getAccountNo(), "payout account accountNo required");
        rejectClientSuppliedMaskedAccountNo(accountNo);
        jdbcTemplate.update("update payout_account set is_default = false, updated_at = CURRENT_TIMESTAMP where user_id = ?", userId);
        jdbcTemplate.update(
                "insert into payout_account (user_id,payment_method,account_name,account_no,masked_account_no,verify_status,is_default,created_at,updated_at) values (?,?,?,?,?,'VERIFIED',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId,
                paymentMethod,
                accountName,
                accountNo,
                maskAccountNo(accountNo)
        );
        return jdbcTemplate.queryForObject("select id from payout_account where user_id = ? and is_default = true order by id desc limit 1", Long.class, userId);
    }

    public PayoutAccountResponse getActivePayoutAccount(Long userId) {
        validateUserId(userId, "payout account");
        return findActivePayoutAccount(userId);
    }

    public WalletBalanceResponse getBalance(Long userId) {
        validateUserId(userId, "balance");
        WalletAccount account = accountOf(userId);
        return new WalletBalanceResponse(
                money(account.getRechargeBalance()),
                money(account.getIncomeBalance()),
                money(account.getFrozenBalance()),
                money(account.getWithdrawableBalance())
        );
    }

    public List<WalletLedgerItemResponse> listLedger(Long userId) {
        validateUserId(userId, "ledger");
        return jdbcTemplate.query(
                "select ledger_no,direction,amount,balance_type,biz_type,biz_no,balance_before,balance_after,status,remark,created_at "
                        + "from wallet_ledger_entry where user_id = ? order by created_at desc, id desc",
                (rs, rowNum) -> new WalletLedgerItemResponse(
                        rs.getString("ledger_no"),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount"),
                        rs.getString("balance_type"),
                        rs.getString("biz_type"),
                        rs.getString("biz_no"),
                        rs.getBigDecimal("balance_before"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("status"),
                        rs.getString("remark"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                userId
        );
    }

    @Transactional
    public LedgerTransactionResponse credit(CreditCommand command) {
        validate(command == null ? null : command.getAmount(), command == null ? null : command.getUserId(), "credit");
        String idempotencyKey = requireText(command.getIdempotencyKey(), "credit idempotencyKey required");
        String cacheKey = cacheKey("CREDIT", command.getUserId(), idempotencyKey);
        LedgerTransactionResponse cached = findLedgerByCacheKey(cacheKey);
        if (cached != null) {
            return cached.asIdempotentReplay();
        }
        String balanceType = normalizeBalanceType(command.getBalanceType());
        BigDecimal amount = money(command.getAmount());
        WalletAccount account = accountOf(command.getUserId());
        BigDecimal balanceBefore = currentBalance(account, balanceType);
        BigDecimal balanceAfter = balanceBefore.add(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        updateBalance(command.getUserId(), balanceType, balanceAfter);
        LedgerTransactionResponse response = new LedgerTransactionResponse(
                generateLedgerNo("CR", command.getUserId(), idempotencyKey),
                command.getUserId(),
                "CREDIT",
                safeText(command.getBizType()),
                safeText(command.getBizNo()),
                balanceType,
                amount,
                balanceBefore,
                balanceAfter,
                LocalDateTime.now(),
                false
        );
        appendLedger(command.getUserId(), response, cacheKey);
        return response;
    }

    @Transactional
    public LedgerTransactionResponse debit(DebitCommand command) {
        validate(command == null ? null : command.getAmount(), command == null ? null : command.getUserId(), "debit");
        String idempotencyKey = requireText(command.getIdempotencyKey(), "debit idempotencyKey required");
        String cacheKey = cacheKey("DEBIT", command.getUserId(), idempotencyKey);
        LedgerTransactionResponse cached = findLedgerByCacheKey(cacheKey);
        if (cached != null) {
            return cached.asIdempotentReplay();
        }
        String balanceType = normalizeBalanceType(command.getBalanceType());
        BigDecimal amount = money(command.getAmount());
        WalletAccount account = accountOf(command.getUserId());
        BigDecimal balanceBefore = currentBalance(account, balanceType);
        BigDecimal balanceAfter = balanceBefore.subtract(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (balanceAfter.compareTo(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)) < 0) {
            throw new IllegalStateException("insufficient wallet balance");
        }
        updateBalance(command.getUserId(), balanceType, balanceAfter);
        LedgerTransactionResponse response = new LedgerTransactionResponse(
                generateLedgerNo("DR", command.getUserId(), idempotencyKey),
                command.getUserId(),
                "DEBIT",
                safeText(command.getBizType()),
                safeText(command.getBizNo()),
                balanceType,
                amount,
                balanceBefore,
                balanceAfter,
                LocalDateTime.now(),
                false
        );
        appendLedger(command.getUserId(), response, cacheKey);
        return response;
    }

    @Transactional
    public WithdrawalResponse createWithdrawal(Long userId, CreateWithdrawalRequest request, String auditNo) {
        validateUserId(userId, "withdrawal");
        if (request == null) {
            throw new IllegalArgumentException("withdrawal request required");
        }
        BigDecimal amount;
        try {
            amount = money(request.getAmount());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("withdrawal amount invalid", ex);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        PayoutAccountRecord payoutAccount = requirePayoutAccount(userId, request.getPayoutAccountId());
        String paymentMethod = payoutAccount.paymentMethod();
        String accountName = payoutAccount.accountName();
        String accountNo = payoutAccount.accountNo();
        WalletAccount account = accountOf(userId);
        BigDecimal withdrawableBefore = money(account.getWithdrawableBalance());
        BigDecimal withdrawableAfter = withdrawableBefore.subtract(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (withdrawableAfter.compareTo(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)) < 0) {
            throw new IllegalStateException("insufficient withdrawable balance");
        }
        BigDecimal frozenAfter = money(account.getFrozenBalance()).add(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        String withdrawalNo = "WD-" + System.currentTimeMillis() + '-' + Math.abs((int) (Math.random() * 100000));
        jdbcTemplate.update(
                "update wallet_account set withdrawable_balance = ?, frozen_balance = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?",
                withdrawableAfter,
                frozenAfter,
                userId
        );
        jdbcTemplate.update(
                "insert into withdrawal_record (withdrawal_no,audit_no,user_id,amount,payment_method,account_name,account_no,status,remark,created_at) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                withdrawalNo,
                safeText(auditNo),
                userId,
                amount,
                paymentMethod,
                accountName,
                accountNo,
                "PENDING",
                safeText(request.getRemark())
        );
        appendWithdrawalLedger(userId, "DEBIT", "WITHDRAW_FREEZE", withdrawalNo, BALANCE_TYPE_WITHDRAWABLE, amount, withdrawableBefore, withdrawableAfter);
        return getWithdrawal(withdrawalNo);
    }

    public List<WithdrawalResponse> listWithdrawals(Long userId) {
        validateUserId(userId, "withdrawal");
        return jdbcTemplate.query(
                "select withdrawal_no,audit_no,user_id,amount,payment_method,account_name,account_no,status,remark,created_at,reviewed_at "
                        + "from withdrawal_record where user_id = ? order by created_at desc, id desc",
                (rs, rowNum) -> toWithdrawalResponse(
                        rs.getString("withdrawal_no"),
                        rs.getString("audit_no"),
                        rs.getLong("user_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_method"),
                        rs.getString("account_name"),
                        rs.getString("account_no"),
                        rs.getString("status"),
                        rs.getString("remark"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("reviewed_at"))
                ),
                userId
        );
    }

    public List<WithdrawalResponse> listAdminWithdrawals(String status, Integer limit) {
        String safeStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.trim().toUpperCase(Locale.ROOT);
        if (safeStatus != null && !"PENDING".equals(safeStatus) && !"APPROVED".equals(safeStatus) && !"REJECTED".equals(safeStatus)) {
            throw new IllegalArgumentException("withdrawal status invalid");
        }
        int safeLimit = limit == null ? 20 : limit;
        if (safeLimit < 1 || safeLimit > 100) {
            throw new IllegalArgumentException("withdrawal limit invalid");
        }
        String sql = "select withdrawal_no,audit_no,user_id,amount,payment_method,account_name,account_no,status,remark,created_at,reviewed_at "
                + "from withdrawal_record "
                + (safeStatus == null ? "" : "where status = ? ")
                + "order by created_at desc, id desc limit ?";
        Object[] args = safeStatus == null ? new Object[]{safeLimit} : new Object[]{safeStatus, safeLimit};
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> toWithdrawalResponse(
                        rs.getString("withdrawal_no"),
                        rs.getString("audit_no"),
                        rs.getLong("user_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_method"),
                        rs.getString("account_name"),
                        rs.getString("account_no"),
                        rs.getString("status"),
                        rs.getString("remark"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("reviewed_at"))
                ),
                args
        );
    }

    @Transactional
    public WithdrawalResponse attachWithdrawalAudit(String withdrawalNo, String auditNo) {
        String safeWithdrawalNo = requireText(withdrawalNo, "withdrawalNo required");
        String safeAuditNo = requireText(auditNo, "auditNo required");
        int changed = jdbcTemplate.update(
                "update withdrawal_record set audit_no = ?, updated_at = CURRENT_TIMESTAMP where withdrawal_no = ?",
                safeAuditNo,
                safeWithdrawalNo
        );
        if (changed == 0) {
            throw new IllegalArgumentException("withdrawal not found");
        }
        return getWithdrawal(safeWithdrawalNo);
    }

    @Transactional
    public void cancelWithdrawalCreation(String withdrawalNo) {
        String safeWithdrawalNo = requireText(withdrawalNo, "withdrawalNo required");
        WithdrawalResponse record = findWithdrawal(safeWithdrawalNo);
        if (record == null || !"PENDING".equals(record.status())) {
            return;
        }
        WalletAccount account = accountOf(record.userId());
        releaseFrozenToWithdrawable(record.userId(), account, record.amount());
        jdbcTemplate.update("update withdrawal_record set status = ?, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP where withdrawal_no = ?", "FAILED", safeWithdrawalNo);
    }

    @Transactional
    public void markWithdrawalReviewed(String withdrawalNo, String status) {
        String safeWithdrawalNo = requireText(withdrawalNo, "withdrawalNo required");
        String safeStatus = requireText(status, "withdrawal status invalid").toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(safeStatus) && !"REJECTED".equals(safeStatus)) {
            throw new IllegalArgumentException("withdrawal status invalid");
        }
        WithdrawalResponse record = getWithdrawal(safeWithdrawalNo);
        if (!"PENDING".equals(record.status())) {
            if (safeStatus.equals(record.status())) {
                return;
            }
            throw new IllegalStateException("withdrawal already reviewed");
        }
        WalletAccount account = accountOf(record.userId());
        if ("APPROVED".equals(safeStatus)) {
            BigDecimal frozenBefore = money(account.getFrozenBalance());
            deductFrozen(record.userId(), account, record.amount());
            appendWithdrawalLedger(record.userId(), "DEBIT", "WITHDRAW_PAYOUT", safeWithdrawalNo, "FROZEN", record.amount(), frozenBefore, frozenBefore.subtract(record.amount()).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        } else {
            BigDecimal withdrawableBefore = money(account.getWithdrawableBalance());
            releaseFrozenToWithdrawable(record.userId(), account, record.amount());
            appendWithdrawalLedger(record.userId(), "CREDIT", "WITHDRAW_RELEASE", safeWithdrawalNo, BALANCE_TYPE_WITHDRAWABLE, record.amount(), withdrawableBefore, withdrawableBefore.add(record.amount()).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        }
        jdbcTemplate.update(
                "update withdrawal_record set status = ?, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP where withdrawal_no = ? and status = ?",
                safeStatus,
                safeWithdrawalNo,
                "PENDING"
        );
    }

    private void deductFrozen(Long userId, WalletAccount account, BigDecimal amount) {
        BigDecimal frozenBefore = money(account.getFrozenBalance());
        BigDecimal frozenAfter = frozenBefore.subtract(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (frozenAfter.compareTo(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)) < 0) {
            throw new IllegalStateException("insufficient frozen balance");
        }
        jdbcTemplate.update("update wallet_account set frozen_balance = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", frozenAfter, userId);
    }

    private void releaseFrozenToWithdrawable(Long userId, WalletAccount account, BigDecimal amount) {
        BigDecimal frozenBefore = money(account.getFrozenBalance());
        BigDecimal frozenAfter = frozenBefore.subtract(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (frozenAfter.compareTo(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)) < 0) {
            throw new IllegalStateException("insufficient frozen balance");
        }
        BigDecimal withdrawableAfter = money(account.getWithdrawableBalance()).add(amount).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        jdbcTemplate.update("update wallet_account set frozen_balance = ?, withdrawable_balance = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", frozenAfter, withdrawableAfter, userId);
    }

    private WalletAccount accountOf(Long userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from wallet_account where user_id = ?", Integer.class, userId);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "insert into wallet_account (user_id,recharge_balance,income_balance,frozen_balance,withdrawable_balance,created_at,updated_at) values (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    userId,
                    zero(),
                    zero(),
                    zero(),
                    zero()
            );
        }
        return jdbcTemplate.queryForObject(
                "select user_id,recharge_balance,income_balance,frozen_balance,withdrawable_balance from wallet_account where user_id = ?",
                (rs, rowNum) -> {
                    WalletAccount account = new WalletAccount();
                    account.setUserId(rs.getLong("user_id"));
                    account.setRechargeBalance(rs.getBigDecimal("recharge_balance"));
                    account.setIncomeBalance(rs.getBigDecimal("income_balance"));
                    account.setFrozenBalance(rs.getBigDecimal("frozen_balance"));
                    account.setWithdrawableBalance(rs.getBigDecimal("withdrawable_balance"));
                    return account;
                },
                userId
        );
    }

    private void appendLedger(Long userId, LedgerTransactionResponse transaction, String idempotencyKey) {
        jdbcTemplate.update(
                "insert into wallet_ledger_entry (ledger_no,user_id,direction,amount,balance_type,biz_type,biz_no,balance_before,balance_after,status,idempotency_key,remark,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                transaction.ledgerNo(),
                userId,
                transaction.direction(),
                transaction.amount(),
                transaction.balanceType(),
                transaction.bizType(),
                transaction.bizNo(),
                transaction.balanceBefore(),
                transaction.balanceAfter(),
                "SUCCESS",
                idempotencyKey,
                withdrawalLedgerRemark(transaction.bizType())
        );
    }

    private void appendWithdrawalLedger(Long userId, String direction, String bizType, String withdrawalNo, String balanceType,
                                        BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter) {
        String cacheKey = cacheKey(bizType, userId, withdrawalNo);
        if (findLedgerByCacheKey(cacheKey) != null) {
            return;
        }
        LedgerTransactionResponse response = new LedgerTransactionResponse(
                generateLedgerNo("WD", userId, bizType + ':' + withdrawalNo),
                userId,
                direction,
                bizType,
                withdrawalNo,
                balanceType,
                amount,
                money(balanceBefore),
                money(balanceAfter),
                LocalDateTime.now(),
                false
        );
        appendLedger(userId, response, cacheKey);
    }

    private String withdrawalLedgerRemark(String bizType) {
        return switch (safeText(bizType) == null ? "" : bizType) {
            case "WITHDRAW_FREEZE" -> "提现申请已冻结可提现余额";
            case "WITHDRAW_PAYOUT" -> "提现审核通过，冻结资金出款";
            case "WITHDRAW_RELEASE" -> "提现审核拒绝，冻结资金解冻";
            default -> null;
        };
    }

    private LedgerTransactionResponse findLedgerByCacheKey(String cacheKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "select ledger_no,user_id,direction,biz_type,biz_no,balance_type,amount,balance_before,balance_after,created_at from wallet_ledger_entry where idempotency_key = ?",
                    (rs, rowNum) -> new LedgerTransactionResponse(
                            rs.getString("ledger_no"),
                            rs.getLong("user_id"),
                            rs.getString("direction"),
                            rs.getString("biz_type"),
                            rs.getString("biz_no"),
                            rs.getString("balance_type"),
                            rs.getBigDecimal("amount"),
                            rs.getBigDecimal("balance_before"),
                            rs.getBigDecimal("balance_after"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            false
                    ),
                    cacheKey
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public WithdrawalResponse getAdminWithdrawal(String withdrawalNo) {
        return getWithdrawal(withdrawalNo);
    }

    private WithdrawalResponse getWithdrawal(String withdrawalNo) {
        WithdrawalResponse response = findWithdrawal(withdrawalNo);
        if (response == null) {
            throw new IllegalArgumentException("withdrawal not found");
        }
        return response;
    }

    private WithdrawalResponse findWithdrawal(String withdrawalNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select withdrawal_no,audit_no,user_id,amount,payment_method,account_name,account_no,status,remark,created_at,reviewed_at from withdrawal_record where withdrawal_no = ?",
                    (rs, rowNum) -> toWithdrawalResponse(
                            rs.getString("withdrawal_no"),
                            rs.getString("audit_no"),
                            rs.getLong("user_id"),
                            rs.getBigDecimal("amount"),
                            rs.getString("payment_method"),
                            rs.getString("account_name"),
                            rs.getString("account_no"),
                            rs.getString("status"),
                            rs.getString("remark"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("reviewed_at"))
                    ),
                    withdrawalNo
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private WithdrawalResponse toWithdrawalResponse(String withdrawalNo, String auditNo, Long userId, BigDecimal amount,
                                                    String paymentMethod, String accountName, String accountNo,
                                                    String status, String remark, LocalDateTime createdAt, LocalDateTime reviewedAt) {
        return new WithdrawalResponse(
                withdrawalNo,
                auditNo,
                userId,
                amount,
                paymentMethod,
                accountName,
                maskAccountNo(accountNo),
                "实名与收款账户待人工一致性复核",
                status,
                remark,
                createdAt,
                reviewedAt
        );
    }

    private void validate(BigDecimal amount, Long userId, String action) {
        validateUserId(userId, action);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(action + " amount must be positive");
        }
        money(amount);
    }

    private void validateUserId(Long userId, String action) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(action + " userId required");
        }
    }

    private BigDecimal currentBalance(WalletAccount account, String balanceType) {
        return switch (balanceType) {
            case BALANCE_TYPE_RECHARGE -> money(account.getRechargeBalance());
            case BALANCE_TYPE_INCOME -> money(account.getIncomeBalance());
            case BALANCE_TYPE_WITHDRAWABLE -> money(account.getWithdrawableBalance());
            default -> throw new IllegalArgumentException("unsupported balanceType");
        };
    }

    private void updateBalance(Long userId, String balanceType, BigDecimal balance) {
        String column = switch (balanceType) {
            case BALANCE_TYPE_RECHARGE -> "recharge_balance";
            case BALANCE_TYPE_INCOME -> "income_balance";
            case BALANCE_TYPE_WITHDRAWABLE -> "withdrawable_balance";
            default -> throw new IllegalArgumentException("unsupported balanceType");
        };
        jdbcTemplate.update("update wallet_account set " + column + " = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", balance, userId);
    }

    private String normalizeBalanceType(String balanceType) {
        String normalized = safeText(balanceType);
        if (normalized == null) {
            return DEFAULT_BALANCE_TYPE;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!BALANCE_TYPE_RECHARGE.equals(normalized)
                && !BALANCE_TYPE_INCOME.equals(normalized)
                && !BALANCE_TYPE_WITHDRAWABLE.equals(normalized)) {
            throw new IllegalArgumentException("unsupported balanceType");
        }
        return normalized;
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount required");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount scale must be <= " + MONEY_SCALE, ex);
        }
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private String requireText(String value, String message) {
        String safe = safeText(value);
        if (safe == null) {
            throw new IllegalArgumentException(message);
        }
        return safe;
    }

    private void rejectClientSuppliedMaskedAccountNo(String accountNo) {
        if (accountNo.contains("*")) {
            throw new IllegalArgumentException("withdrawal accountNo must be backend-owned raw account reference");
        }
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String cacheKey(String direction, Long userId, String idempotencyKey) {
        return direction + ':' + userId + ':' + sha256(idempotencyKey);
    }

    private String generateLedgerNo(String prefix, Long userId, String idempotencyKey) {
        String seed = prefix + ':' + userId + ':' + idempotencyKey + ':' + System.nanoTime();
        return prefix + '-' + sha256(seed).substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private String maskAccountNo(String accountNo) {
        String safe = safeText(accountNo);
        if (safe == null) {
            return "****";
        }
        if (safe.contains("@")) {
            int at = safe.indexOf('@');
            String local = safe.substring(0, at);
            String domain = safe.substring(at);
            if (local.length() <= 1) {
                return "*" + domain;
            }
            return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
        }
        String compact = safe.replaceAll("\\s+", "");
        if (compact.length() <= 4) {
            return "****";
        }
        if (compact.length() >= 8) {
            return compact.substring(0, 4) + " **** **** " + compact.substring(compact.length() - 4);
        }
        return "****" + compact.substring(compact.length() - 4);
    }

    private PayoutAccountRecord requirePayoutAccount(Long userId, Long payoutAccountId) {
        if (payoutAccountId == null || payoutAccountId <= 0) {
            throw new IllegalArgumentException("payout account binding required");
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id,user_id,payment_method,account_name,account_no,masked_account_no,verify_status from payout_account where id = ? and user_id = ? and verify_status = 'VERIFIED'",
                    (rs, rowNum) -> new PayoutAccountRecord(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("payment_method"),
                            rs.getString("account_name"),
                            rs.getString("account_no"),
                            rs.getString("masked_account_no"),
                            rs.getString("verify_status")
                    ),
                    payoutAccountId,
                    userId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("payout account binding invalid", ex);
        }
    }

    private PayoutAccountResponse findActivePayoutAccount(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id,payment_method,account_name,masked_account_no,verify_status from payout_account where user_id = ? and is_default = true order by id desc limit 1",
                    (rs, rowNum) -> new PayoutAccountResponse(
                            rs.getLong("id"),
                            rs.getString("payment_method"),
                            rs.getString("account_name"),
                            rs.getString("masked_account_no"),
                            rs.getString("verify_status")
                    ),
                    userId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private record PayoutAccountRecord(
            Long id,
            Long userId,
            String paymentMethod,
            String accountName,
            String accountNo,
            String maskedAccountNo,
            String verifyStatus
    ) {
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
