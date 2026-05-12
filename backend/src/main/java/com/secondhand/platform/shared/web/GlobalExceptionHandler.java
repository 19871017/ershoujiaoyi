package com.secondhand.platform.shared.web;

import com.secondhand.platform.shared.kernel.Result;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Set<String> SAFE_BUSINESS_MESSAGES = Set.of(
            "login request required",
            "mobile required",
            "mobile invalid",
            "password required",
            "conversation participants required",
            "message context required",
            "message type required",
            "withdrawal request required",
            "withdrawal amount invalid",
            "withdrawal amount must be positive",
            "withdrawal paymentMethod required",
            "withdrawal accountName required",
            "withdrawal accountNo required",
            "insufficient withdrawable balance",
            "report targetType required",
            "report targetId required",
            "report reason required",
            "audit record not found",
            "audit record already reviewed",
            "X-User-Id required",
            "X-User-Id must be positive",
            "X-User-Id invalid",
            "user session invalid",
            "requestNo or clientGiftId required",
            "withdrawal already reviewed",
            "withdrawal status invalid",
            "withdrawal not found",
            "after-sales already reviewed",
            "after-sales status invalid",
            "after-sales record not found",
            "insufficient frozen balance",
            "giftCode or giftId required",
            "receiverId required",
            "gift quantity must be between 1 and 99",
            "cannot send gift to yourself",
            "unsupported gift",
            "insufficient wallet balance",
            "rechargeNo required",
            "recharge request required",
            "recharge amount must be positive",
            "amount scale must be <= 2",
            "channel required",
            "recharge order not found",
            "userId required",
            "goodsId required",
            "buyerId required",
            "trade-rule-not-accepted",
            "product already has pending order",
            "orderNo required",
            "order-not-found",
            "order-not-payable",
            "order-payment-inconsistent",
            "order-buyer-mismatch",
            "reviewerId required",
            "review request required",
            "descriptionScore invalid",
            "serviceScore invalid",
            "shippingScore invalid",
            "review content required",
            "review content invalid",
            "notification type invalid",
            "notificationNo invalid",
            "notification not found",
            "profile request required",
            "nickname invalid",
            "mainRole invalid",
            "city invalid",
            "bio invalid",
            "identity fields must be server-derived",
            "daily registration limit exceeded"
    );

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleSecurityException(SecurityException exception) {
        return Result.fail("forbidden");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        return Result.fail(resolveSafeBusinessMessage(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError == null) {
            return Result.fail("request invalid");
        }
        return Result.fail(fieldError.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        return Result.fail("request body invalid");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleIllegalStateException(IllegalStateException exception) {
        return Result.fail(resolveSafeBusinessMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception exception) {
        return Result.fail("internal server error");
    }

    private String resolveSafeBusinessMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && SAFE_BUSINESS_MESSAGES.contains(message)) {
            return message;
        }
        return "bad request";
    }
}
