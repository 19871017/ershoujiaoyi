package com.secondhand.platform.shared.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    private final boolean allowLanOrigins;

    public CorsConfig(@Value("${app.cors.allow-lan-origins:false}") boolean allowLanOrigins) {
        this.allowLanOrigins = allowLanOrigins;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                List<String> allowedOrigins = new ArrayList<>(List.of("http://old.tiklxd09.club", "https://old.tiklxd09.club", "http://localhost:*", "http://127.0.0.1:*"));
                if (allowLanOrigins) {
                    allowedOrigins.add("http://192.168.*:*");
                }
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
