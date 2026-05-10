package com.secondhand.platform.modules.address;

public record AddressResponse(
        Long addressId,
        Long userId,
        String name,
        String mobile,
        String provinceCity,
        String detail,
        boolean isDefault,
        String createdAt,
        String updatedAt
) {
}
