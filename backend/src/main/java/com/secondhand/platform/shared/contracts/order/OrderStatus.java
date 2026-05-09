package com.secondhand.platform.shared.contracts.order;

public enum OrderStatus {
    PENDING_PAY,
    PAID,
    WAIT_SHIP,
    SHIPPED,
    COMPLETED,
    AFTER_SALE,
    REFUNDED,
    CLOSED
}
