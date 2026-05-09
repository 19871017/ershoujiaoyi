package com.secondhand.platform.modules.location;

import com.secondhand.platform.shared.kernel.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class LocationController {
    private final LocationApplicationService locationApplicationService;

    public LocationController(LocationApplicationService locationApplicationService) {
        this.locationApplicationService = locationApplicationService;
    }

    @GetMapping("/config")
    public Result<LocationConfigResponse> config() {
        return Result.ok(locationApplicationService.getConfig());
    }

    @PostMapping("/reverse-geocode")
    public Result<ReverseGeocodeResponse> reverseGeocode(@RequestBody ReverseGeocodeRequest request) {
        return Result.ok(locationApplicationService.reverse(request));
    }
}
