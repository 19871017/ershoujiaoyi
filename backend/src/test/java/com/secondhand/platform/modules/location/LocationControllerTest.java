package com.secondhand.platform.modules.location;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocationControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        LocationApplicationService service = new LocationApplicationService(new BaiduReverseGeocodeClient(), "", jdbcTemplate);
        mvc = MockMvcBuilders.standaloneSetup(new LocationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reverseGeocodeExposesSafeConfigurationErrorInsteadOfFallbackCity() throws Exception {
        mvc.perform(post("/api/location/reverse-geocode")
                        .contentType("application/json")
                        .content("{\"latitude\":23.1291,\"longitude\":113.2644}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("location reverse geocode not configured"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
