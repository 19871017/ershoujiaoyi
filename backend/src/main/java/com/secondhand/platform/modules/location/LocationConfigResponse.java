package com.secondhand.platform.modules.location;

public record LocationConfigResponse(
    String provider,
    boolean enabled,
    boolean configured,
    String defaultCity,
    String defaultProvince,
    String coordinateType,
    String updatedAt
) {
}
