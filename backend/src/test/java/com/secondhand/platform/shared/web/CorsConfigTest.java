package com.secondhand.platform.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CorsConfigTest {
    @Test
    void productionProfilesOnlyAllowPublicSiteOrigins() {
        List<String> origins = cors(false, "prod").allowedOriginPatterns();

        assertEquals(List.of("http://old.tiklxd09.club", "https://old.tiklxd09.club"), origins);
    }

    @Test
    void localOriginsRequireDevelopmentProfileWithoutProductionProfile() {
        List<String> origins = cors(false, "local").allowedOriginPatterns();

        assertEquals(List.of(
                "http://old.tiklxd09.club",
                "https://old.tiklxd09.club",
                "http://localhost:*",
                "http://127.0.0.1:*"), origins);
    }

    @Test
    void productionProfileBlocksLocalOriginsEvenWhenDevelopmentProfileIsAlsoActive() {
        List<String> origins = cors(true, "local", "production").allowedOriginPatterns();

        assertEquals(List.of("http://old.tiklxd09.club", "https://old.tiklxd09.club"), origins);
    }

    @Test
    void lanOriginsRequireDevelopmentProfileAndExplicitLanSwitch() {
        List<String> origins = cors(true, "dev").allowedOriginPatterns();

        assertEquals(List.of(
                "http://old.tiklxd09.club",
                "https://old.tiklxd09.club",
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*:*"), origins);
    }

    private CorsConfig cors(boolean allowLanOrigins, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new CorsConfig(allowLanOrigins, environment);
    }
}
