package com.secondhand.platform.modules.location;

import java.math.BigDecimal;

public record ReverseGeocodeResponse(
    String provider,
    String province,
    String city,
    String district,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    boolean fallback
) {
}
