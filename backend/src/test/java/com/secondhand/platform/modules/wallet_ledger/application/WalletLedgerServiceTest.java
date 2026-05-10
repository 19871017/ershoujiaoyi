package com.secondhand.platform.modules.wallet_ledger.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.wallet_ledger.CreateWithdrawalRequest;
import com.secondhand.platform.modules.wallet_ledger.PayoutAccountRequest;
import com.secondhand.platform.modules.wallet_ledger.PayoutAccountResponse;
import com.secondhand.platform.modules.wallet_ledger.WalletBalanceResponse;
import com.secondhand.platform.modules.wallet_ledger.WithdrawalResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class WalletLedgerServiceTest {
    private EmbeddedDatabase database;
    private WalletLedgerService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        service = new WalletLedgerService(new JdbcTemplate(database));
    }

    @Test
    void creditShouldBeIdempotentAndOnlyCreateOneLedgerEntry() {
        CreditCommand command = credit(1L, "credit-001", "RECHARGE", "100.00");

        LedgerTransactionResponse first = service.credit(command);
        LedgerTransactionResponse replay = service.credit(command);

        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(first.ledgerNo(), replay.ledgerNo());
        assertMoney("100.00", service.getBalance(1L).getRechargeBalance());
        assertEquals(1, service.listLedger(1L).size());
    }

    @Test
    void debitShouldBeIdempotentAndOnlyDeductOnce() {
        service.credit(credit(1L, "seed", "RECHARGE", "100.00"));
        DebitCommand command = debit(1L, "debit-001", "RECHARGE", "30.00");

        LedgerTransactionResponse first = service.debit(command);
        LedgerTransactionResponse replay = service.debit(command);

        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(first.ledgerNo(), replay.ledgerNo());
        assertMoney("70.00", service.getBalance(1L).getRechargeBalance());
        assertEquals(2, service.listLedger(1L).size());
    }

    @Test
    void debitShouldRejectInsufficientBalanceWithoutChangingBalance() {
        service.credit(credit(1L, "seed", "RECHARGE", "20.00"));

        assertThrows(IllegalStateException.class, () -> service.debit(debit(1L, "too-much", "RECHARGE", "20.01")));

        assertMoney("20.00", service.getBalance(1L).getRechargeBalance());
        assertEquals(1, service.listLedger(1L).size());
    }

    @Test
    void createWithdrawalShouldFreezeWithdrawableBalanceAndRecordFreezeLedger() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));

        WithdrawalResponse withdrawal = service.createWithdrawal(1L, withdrawal("50.00"), "AU-WD-1");

        assertEquals("PENDING", withdrawal.status());
        assertEquals("AU-WD-1", withdrawal.auditNo());
        assertMoney("50.00", withdrawal.amount());
        WalletBalanceResponse balance = service.getBalance(1L);
        assertMoney("30.00", balance.getWithdrawableBalance());
        assertMoney("50.00", balance.getFrozenBalance());
        assertEquals(2, service.listLedger(1L).size());
        assertEquals("WITHDRAW_FREEZE", service.listLedger(1L).get(0).businessType());
        assertEquals(withdrawal.withdrawalNo(), service.listLedger(1L).get(0).businessId());
    }

    @Test
    void bindPayoutAccountShouldRejectClientSuppliedMaskedAccountNumber() {
        PayoutAccountRequest request = payoutAccount("ALIPAY", "Alice", "6222 **** **** 8088");

        assertThrows(IllegalArgumentException.class, () -> service.bindPayoutAccount(1L, request));

        assertTrue(service.listWithdrawals(1L).isEmpty());
    }

    @Test
    void createWithdrawalShouldRequireBackendOwnedPayoutAccountBindingReference() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setAccountNo("6222020202020208088");
        request.setPayoutAccountId(null);

        assertThrows(IllegalArgumentException.class, () -> service.createWithdrawal(1L, request, "AU-WD-1"));

        WalletBalanceResponse balance = service.getBalance(1L);
        assertMoney("80.00", balance.getWithdrawableBalance());
        assertMoney("0.00", balance.getFrozenBalance());
        assertEquals(1, service.listLedger(1L).size());
        assertTrue(service.listWithdrawals(1L).isEmpty());
    }

    @Test
    void createWithdrawalShouldRejectClientSuppliedMaskedAccountNumberBeforeFreezingFunds() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("ALIPAY", "Alice", "6222020202020208088"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(accountId);
        request.setAccountNo("6222 **** **** 8088");

        assertThrows(IllegalArgumentException.class, () -> service.createWithdrawal(1L, request, "AU-WD-1"));

        WalletBalanceResponse balance = service.getBalance(1L);
        assertMoney("80.00", balance.getWithdrawableBalance());
        assertMoney("0.00", balance.getFrozenBalance());
        assertEquals(1, service.listLedger(1L).size());
        assertTrue(service.listWithdrawals(1L).isEmpty());
    }

    @Test
    void createWithdrawalShouldUseVerifiedOwnerPayoutAccountAndIgnoreClientRawAccountNumber() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("ALIPAY", "Alice", "6222020202020208088"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(accountId);
        request.setAccountNo("client-supplied-raw-should-not-persist");
        request.setAccountName("Mallory");

        WithdrawalResponse withdrawal = service.createWithdrawal(1L, request, "AU-WD-1");

        assertEquals("Alice", withdrawal.accountName());
        assertEquals("6222 **** **** 8088", withdrawal.maskedAccountNo());
        assertFalse(withdrawal.maskedAccountNo().contains("client-supplied"));
        String storedAccount = new JdbcTemplate(database).queryForObject("select account_no from withdrawal_record where withdrawal_no = ?", String.class, withdrawal.withdrawalNo());
        assertEquals("6222020202020208088", storedAccount);
    }

    @Test
    void createWithdrawalShouldRejectPayoutAccountOwnedByAnotherUser() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long otherAccountId = service.bindPayoutAccount(2L, payoutAccount("ALIPAY", "Bob", "bob@example.com"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(otherAccountId);

        assertThrows(IllegalArgumentException.class, () -> service.createWithdrawal(1L, request, "AU-WD-1"));

        assertMoney("80.00", service.getBalance(1L).getWithdrawableBalance());
        assertTrue(service.listWithdrawals(1L).isEmpty());
    }

    @Test
    void bindPayoutAccountShouldPersistMaskedOwnerScopedAccountWithoutExposingRawValue() {
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("BANK_CARD", "Alice", "6222020202020208088"));

        PayoutAccountResponse bound = service.getActivePayoutAccount(1L);

        assertEquals(accountId, bound.payoutAccountId());
        assertEquals("BANK_CARD", bound.paymentMethod());
        assertEquals("Alice", bound.accountName());
        assertEquals("6222 **** **** 8088", bound.maskedAccountNo());
        assertEquals("VERIFIED", bound.verifyStatus());
        assertFalse(bound.maskedAccountNo().contains("020202020"));
    }

    @Test
    void withdrawalResponsesShouldMaskAccountNumberForUserListsAndCreation() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("ALIPAY", "Alice", "6222020202020208088"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(accountId);

        WithdrawalResponse created = service.createWithdrawal(1L, request, "AU-WD-1");
        WithdrawalResponse listed = service.listWithdrawals(1L).get(0);

        assertEquals("6222 **** **** 8088", created.maskedAccountNo());
        assertEquals("6222 **** **** 8088", listed.maskedAccountNo());
        assertFalse(created.maskedAccountNo().contains("020202020"));
        assertEquals("Alice", created.accountName());
        assertEquals("实名与收款账户待人工一致性复核", created.accountVerifyStatus());
    }

    @Test
    void withdrawalResponsesShouldNotExposeRawAccountNumberField() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("ALIPAY", "Alice", "6222020202020208088"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(accountId);

        WithdrawalResponse created = service.createWithdrawal(1L, request, "AU-WD-1");
        WithdrawalResponse listed = service.listWithdrawals(1L).get(0);

        assertEquals("6222 **** **** 8088", created.maskedAccountNo());
        assertEquals("6222 **** **** 8088", listed.maskedAccountNo());
    }

    @Test
    void adminWithdrawalDetailShouldExposeOnlyMaskedAccountNumberAndReviewHints() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        Long accountId = service.bindPayoutAccount(1L, payoutAccount("ALIPAY", "Alice", "alice@example.com"));
        CreateWithdrawalRequest request = withdrawal("50.00");
        request.setPayoutAccountId(accountId);
        WithdrawalResponse withdrawal = service.createWithdrawal(1L, request, "AU-WD-1");

        WithdrawalResponse adminDetail = service.getAdminWithdrawal(withdrawal.withdrawalNo());

        assertEquals("a***e@example.com", adminDetail.maskedAccountNo());
        assertEquals("实名与收款账户待人工一致性复核", adminDetail.accountVerifyStatus());
        assertFalse(adminDetail.maskedAccountNo().contains("alice@"));
    }

    @Test
    void adminWithdrawalListShouldFilterByStatusLimitResultsAndMaskAccountNumbers() {
        service.credit(credit(1L, "income-1", "WITHDRAWABLE", "80.00"));
        service.credit(credit(2L, "income-2", "WITHDRAWABLE", "80.00"));
        WithdrawalResponse pending = service.createWithdrawal(1L, withdrawal("20.00"), "AU-WD-1");
        WithdrawalResponse approved = service.createWithdrawal(2L, withdrawal(2L, "30.00"), "AU-WD-2");
        service.markWithdrawalReviewed(approved.withdrawalNo(), "APPROVED");

        List<WithdrawalResponse> pendingRows = service.listAdminWithdrawals("PENDING", 20);
        List<WithdrawalResponse> allRows = service.listAdminWithdrawals(null, 1);

        assertEquals(1, pendingRows.size());
        assertEquals(pending.withdrawalNo(), pendingRows.get(0).withdrawalNo());
        assertEquals("a***e@example.com", pendingRows.get(0).maskedAccountNo());
        assertFalse(pendingRows.get(0).maskedAccountNo().contains("alice@"));
        assertEquals(1, allRows.size());
        assertThrows(IllegalArgumentException.class, () -> service.listAdminWithdrawals("PREVIEW", 20));
        assertThrows(IllegalArgumentException.class, () -> service.listAdminWithdrawals("PENDING", 101));
    }

    @Test
    void approveWithdrawalShouldDeductFrozenAndRecordPayoutLedgerOnlyOnce() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        WithdrawalResponse withdrawal = service.createWithdrawal(1L, withdrawal("50.00"), "AU-WD-1");

        service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "APPROVED");
        service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "APPROVED");

        WalletBalanceResponse balance = service.getBalance(1L);
        assertMoney("30.00", balance.getWithdrawableBalance());
        assertMoney("0.00", balance.getFrozenBalance());
        assertEquals("APPROVED", service.listWithdrawals(1L).get(0).status());
        assertEquals(3, service.listLedger(1L).size());
        assertEquals(1, ledgerCount("WITHDRAW_FREEZE", withdrawal.withdrawalNo()));
        assertEquals(1, ledgerCount("WITHDRAW_PAYOUT", withdrawal.withdrawalNo()));
    }

    @Test
    void rejectWithdrawalShouldReleaseFrozenAndRecordReleaseLedgerOnlyOnce() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        WithdrawalResponse withdrawal = service.createWithdrawal(1L, withdrawal("50.00"), "AU-WD-1");

        service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "REJECTED");
        service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "REJECTED");

        WalletBalanceResponse balance = service.getBalance(1L);
        assertMoney("80.00", balance.getWithdrawableBalance());
        assertMoney("0.00", balance.getFrozenBalance());
        assertEquals("REJECTED", service.listWithdrawals(1L).get(0).status());
        assertEquals(3, service.listLedger(1L).size());
        assertEquals(1, ledgerCount("WITHDRAW_FREEZE", withdrawal.withdrawalNo()));
        assertEquals(1, ledgerCount("WITHDRAW_RELEASE", withdrawal.withdrawalNo()));
    }

    @Test
    void repeatedWithdrawalReviewWithDifferentStatusShouldBeRejected() {
        service.credit(credit(1L, "income", "WITHDRAWABLE", "80.00"));
        WithdrawalResponse withdrawal = service.createWithdrawal(1L, withdrawal("50.00"), "AU-WD-1");
        service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "APPROVED");

        assertThrows(IllegalStateException.class, () -> service.markWithdrawalReviewed(withdrawal.withdrawalNo(), "REJECTED"));
    }

    @Test
    void walletDataShouldSurviveServiceRecreationWithSameDatabase() {
        service.credit(credit(7L, "income", "WITHDRAWABLE", "120.00"));
        WithdrawalResponse withdrawal = service.createWithdrawal(7L, withdrawal(7L, "40.00"), "AU-WD-DB");

        WalletLedgerService reloaded = new WalletLedgerService(new JdbcTemplate(database));

        WalletBalanceResponse balance = reloaded.getBalance(7L);
        assertMoney("80.00", balance.getWithdrawableBalance());
        assertMoney("40.00", balance.getFrozenBalance());
        assertEquals(withdrawal.withdrawalNo(), reloaded.listWithdrawals(7L).get(0).withdrawalNo());
        assertEquals(2, reloaded.listLedger(7L).size());
        assertEquals("WITHDRAW_FREEZE", reloaded.listLedger(7L).get(0).businessType());
    }

    private int ledgerCount(String businessType, String businessId) {
        return service.listLedger(1L).stream()
                .filter(item -> businessType.equals(item.businessType()))
                .filter(item -> businessId.equals(item.businessId()))
                .toList()
                .size();
    }

    private CreditCommand credit(Long userId, String key, String balanceType, String amount) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setIdempotencyKey(key);
        command.setBizType("TEST");
        command.setBizNo(key);
        command.setBalanceType(balanceType);
        command.setAmount(new BigDecimal(amount));
        return command;
    }

    private DebitCommand debit(Long userId, String key, String balanceType, String amount) {
        DebitCommand command = new DebitCommand();
        command.setUserId(userId);
        command.setIdempotencyKey(key);
        command.setBizType("TEST");
        command.setBizNo(key);
        command.setBalanceType(balanceType);
        command.setAmount(new BigDecimal(amount));
        return command;
    }

    private CreateWithdrawalRequest withdrawal(Long userId, String amount) {
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        Long accountId = service.bindPayoutAccount(userId, payoutAccount("ALIPAY", "Alice", "alice@example.com"));
        request.setAmount(new BigDecimal(amount));
        request.setPayoutAccountId(accountId);
        request.setPaymentMethod("alipay");
        request.setAccountName("Alice");
        request.setAccountNo("alice@example.com");
        request.setRemark("test withdrawal");
        return request;
    }

    private CreateWithdrawalRequest withdrawal(String amount) {
        return withdrawal(1L, amount);
    }

    private PayoutAccountRequest payoutAccount(String paymentMethod, String accountName, String accountNo) {
        PayoutAccountRequest request = new PayoutAccountRequest();
        request.setPaymentMethod(paymentMethod);
        request.setAccountName(accountName);
        request.setAccountNo(accountNo);
        return request;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }
}
