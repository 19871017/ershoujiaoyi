package com.secondhand.platform.shared.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    private static final List<String> PUBLIC_SITE_ORIGIN_PATTERNS = List.of(
            "http://old.tiklxd09.club",
            "https://old.tiklxd09.club");

    private final boolean allowLanOrigins;
    private final Environment environment;

    public CorsConfig(
            @Value("${app.cors.allow-lan-origins:false}") boolean allowLanOrigins,
            Environment environment) {
        this.allowLanOrigins = allowLanOrigins;
        this.environment = environment;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                List<String> allowedOrigins = allowedOriginPatterns();
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    List<String> allowedOriginPatterns() {
        List<String> allowedOrigins = new ArrayList<>(PUBLIC_SITE_ORIGIN_PATTERNS);
        if (!isDevelopmentProfile()) {
            return allowedOrigins;
        }
        allowedOrigins.add("http://localhost:*");
        allowedOrigins.add("http://127.0.0.1:*");
        if (allowLanOrigins) {
            allowedOrigins.add("http://192.168.*:*");
        }
        return allowedOrigins;
    }

    private boolean isDevelopmentProfile() {
        if (environment == null) {
            return false;
        }
        boolean production = false;
        boolean development = false;
        for (String profile : environment.getActiveProfiles()) {
            String normalizedProfile = profile.toLowerCase(Locale.ROOT);
            if ("prod".equals(normalizedProfile) || "production".equals(normalizedProfile)) {
                production = true;
            } else if ("dev".equals(normalizedProfile) || "local".equals(normalizedProfile)) {
                development = true;
            }
        }
        return development && !production;
    }
}
