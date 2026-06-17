package com.secondhand.platform.modules.auth.application;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultIpLocationResolver implements IpLocationResolver {
    private static final String UNKNOWN_LOCATION = "IP属地未知";
    private static final Pattern PROVINCE_PATTERN = Pattern.compile("\"province\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern CITY_PATTERN = Pattern.compile("\"city\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern REGION_PATTERN = Pattern.compile("\"region\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("\"country\"\\s*:\\s*\"([^\"]*)\"");

    private final String apiUrlTemplate;
    private final HttpClient httpClient;

    public DefaultIpLocationResolver() {
        this("", HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public DefaultIpLocationResolver(@Value("${IP_LOCATION_API_URL:}") String apiUrlTemplate) {
        this(apiUrlTemplate, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    DefaultIpLocationResolver(String apiUrlTemplate, HttpClient httpClient) {
        this.apiUrlTemplate = apiUrlTemplate == null ? "" : apiUrlTemplate.trim();
        this.httpClient = httpClient;
    }

    @Override
    public String resolve(String clientIp) {
        String normalized = normalize(clientIp);
        if ("unknown".equals(normalized)) {
            return UNKNOWN_LOCATION;
        }
        if ("127.0.0.1".equals(normalized) || "::1".equals(normalized) || "localhost".equals(normalized)) {
            return "本机";
        }
        if (normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            return "内网";
        }
        if (apiUrlTemplate.isBlank()) {
            return UNKNOWN_LOCATION;
        }
        return resolveFromHttpProvider(normalized);
    }

    private String resolveFromHttpProvider(String clientIp) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(expandUrl(clientIp)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return UNKNOWN_LOCATION;
            }
            return parseLocation(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return UNKNOWN_LOCATION;
        } catch (Exception exception) {
            return UNKNOWN_LOCATION;
        }
    }

    private String expandUrl(String clientIp) {
        String encodedIp = URLEncoder.encode(clientIp, StandardCharsets.UTF_8);
        if (apiUrlTemplate.contains("{ip}")) {
            return apiUrlTemplate.replace("{ip}", encodedIp);
        }
        String separator = apiUrlTemplate.contains("?") ? "&" : "?";
        return apiUrlTemplate + separator + "ip=" + encodedIp;
    }

    private String parseLocation(String body) {
        String province = textValue(body, PROVINCE_PATTERN);
        String city = textValue(body, CITY_PATTERN);
        String region = textValue(body, REGION_PATTERN);
        String country = textValue(body, COUNTRY_PATTERN);
        String location = joinLocation(province, city);
        if (!location.isBlank()) {
            return location;
        }
        location = joinLocation(country, region);
        return location.isBlank() ? UNKNOWN_LOCATION : location;
    }

    private String joinLocation(String first, String second) {
        String left = sanitizeLocation(first);
        String right = sanitizeLocation(second);
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank() || left.equals(right)) {
            return left;
        }
        return left + " " + right;
    }

    private String textValue(String body, Pattern pattern) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String sanitizeLocation(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || "unknown".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized)
                || normalized.length() > 32) {
            return "";
        }
        return normalized;
    }

    private String normalize(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim().toLowerCase(Locale.ROOT);
    }
}
