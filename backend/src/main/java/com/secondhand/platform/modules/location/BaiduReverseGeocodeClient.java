package com.secondhand.platform.modules.location;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BaiduReverseGeocodeClient {
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*(\\d+)");
    private static final Pattern PROVINCE_PATTERN = Pattern.compile("\"province\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern CITY_PATTERN = Pattern.compile("\"city\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DISTRICT_PATTERN = Pattern.compile("\"district\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("\"formatted_address\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    public ReverseGeocodeResponse reverse(String ak, String coordinateType, BigDecimal latitude, BigDecimal longitude) {
        if (isBlank(ak)) {
            throw new IllegalStateException("baidu map ak not configured");
        }
        String location = latitude.toPlainString() + "," + longitude.toPlainString();
        String coordType = isBlank(coordinateType) ? "wgs84ll" : coordinateType.trim();
        String url = "https://api.map.baidu.com/reverse_geocoding/v3/?output=json&extensions_poi=0"
            + "&coordtype=" + encode(coordType)
            + "&location=" + encode(location)
            + "&ak=" + encode(ak);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("baidu map request failed");
            }
            String body = response.body();
            int status = intValue(body, STATUS_PATTERN, -1);
            if (status != 0) {
                throw new IllegalStateException("baidu map response status " + status);
            }
            return new ReverseGeocodeResponse(
                GeoProvider.BAIDU.name(),
                textValue(body, PROVINCE_PATTERN),
                textValue(body, CITY_PATTERN),
                textValue(body, DISTRICT_PATTERN),
                textValue(body, ADDRESS_PATTERN),
                latitude,
                longitude,
                false
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("baidu map request interrupted");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("baidu map reverse geocode failed");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String textValue(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private int intValue(String body, Pattern pattern, int fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
