package com.secondhand.platform.modules.order;

import com.secondhand.platform.modules.order.application.CreateOrderRequest;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.order.application.ShipOrderRequest;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderApplicationService orderApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public OrderController(OrderApplicationService orderApplicationService, CurrentUserResolver currentUserResolver) {
        this.orderApplicationService = orderApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public Result<List<OrderListItemResponse>> listOrders(@RequestParam(defaultValue = "buyer") String role,
                                                          @RequestParam(defaultValue = "ALL") String status,
                                                          HttpServletRequest httpRequest) {
        long userId = currentUserResolver.resolve(httpRequest);
        return Result.ok(orderApplicationService.listOrders(userId, role, status));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDetailResponse> detailOrder(@PathVariable String orderNo, HttpServletRequest httpRequest) {
        long userId = currentUserResolver.resolve(httpRequest);
        return Result.ok(orderApplicationService.detailOrder(orderNo, userId));
    }

    @PostMapping
    public Result<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        long buyerId = currentUserResolver.resolve(httpRequest);
        return Result.ok(orderApplicationService.createOrder(request, buyerId));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<PayOrderResponse> payOrder(@PathVariable String orderNo, HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(orderApplicationService.payOrder(orderNo, userId));
    }

    @PostMapping("/{orderNo}/ship")
    public Result<ShipOrderResponse> shipOrder(@PathVariable String orderNo, @RequestBody ShipOrderRequest body, HttpServletRequest request) {
        long sellerId = currentUserResolver.resolve(request);
        return Result.ok(orderApplicationService.shipOrder(orderNo, sellerId, body));
    }

    @PostMapping("/{orderNo}/confirm-receipt")
    public Result<OrderDetailResponse> confirmReceipt(@PathVariable String orderNo, HttpServletRequest request) {
        long buyerId = currentUserResolver.resolve(request);
        return Result.ok(orderApplicationService.confirmReceipt(orderNo, buyerId));
    }
}
