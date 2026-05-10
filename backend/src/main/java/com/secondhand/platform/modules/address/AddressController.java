package com.secondhand.platform.modules.address;

import com.secondhand.platform.modules.address.application.AddressApplicationService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/addresses")
public class AddressController {
    private final AddressApplicationService addressApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public AddressController(AddressApplicationService addressApplicationService, CurrentUserResolver currentUserResolver) {
        this.addressApplicationService = addressApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public Result<List<AddressResponse>> list(HttpServletRequest request) {
        return Result.ok(addressApplicationService.listAddresses(currentUserResolver.resolve(request)));
    }

    @PostMapping
    public Result<AddressResponse> save(@RequestBody AddressRequest body, HttpServletRequest request) {
        return Result.ok(addressApplicationService.saveAddress(currentUserResolver.resolve(request), body));
    }

    @PostMapping("/{addressId}/default")
    public Result<AddressResponse> setDefault(@PathVariable Long addressId, HttpServletRequest request) {
        return Result.ok(addressApplicationService.setDefault(currentUserResolver.resolve(request), addressId));
    }

    @DeleteMapping("/{addressId}")
    public Result<Boolean> delete(@PathVariable Long addressId, HttpServletRequest request) {
        addressApplicationService.deleteAddress(currentUserResolver.resolve(request), addressId);
        return Result.ok(true);
    }
}
