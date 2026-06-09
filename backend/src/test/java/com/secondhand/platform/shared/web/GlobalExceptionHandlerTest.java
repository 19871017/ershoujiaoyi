package com.secondhand.platform.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingApiResourceReturnsNotFoundEnvelope() {
        var response = handler.handleNoResourceFoundException(new NoResourceFoundException(HttpMethod.GET, "api/home"));

        assertFalse(response.isSuccess());
        assertEquals("not found", response.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, findNoResourceStatus());
    }

    private HttpStatus findNoResourceStatus() {
        try {
            var method = GlobalExceptionHandler.class.getDeclaredMethod(
                    "handleNoResourceFoundException",
                    NoResourceFoundException.class
            );
            return method.getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value();
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
