package com.secondhand.platform.modules.audit.application;

import java.math.BigDecimal;

public record AdminDashboardSummary(
        String status,
        int pendingAudits,
        int approvedAudits,
        int rejectedAudits,
        int pendingWithdrawals,
        int pendingAfterSales,
        int activeUsers,
        int todayOrders,
        BigDecimal grossMerchandiseValue
) {
}
