package com.secondhand.platform.modules.gift;

import com.secondhand.platform.modules.gift.application.GiftApplicationService;
import com.secondhand.platform.modules.gift.application.SendGiftRequest;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gifts")
public class GiftController {
    private final GiftApplicationService giftApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public GiftController(GiftApplicationService giftApplicationService, CurrentUserResolver currentUserResolver) {
        this.giftApplicationService = giftApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/catalog")
    public Result<List<GiftCatalogItemResponse>> catalog() {
        return Result.ok(giftApplicationService.listCatalog());
    }

    @GetMapping("/received")
    public Result<List<ReceivedGiftItemResponse>> received(HttpServletRequest httpRequest) {
        Long receiverId = currentUserResolver.resolve(httpRequest);
        return Result.ok(giftApplicationService.listReceivedGifts(receiverId));
    }

    @PostMapping("/send")
    public Result<SendGiftResponse> sendGift(@RequestBody SendGiftRequest request, HttpServletRequest httpRequest) {
        Long senderId = currentUserResolver.resolve(httpRequest);
        return Result.ok(giftApplicationService.sendGift(senderId, request));
    }
}
