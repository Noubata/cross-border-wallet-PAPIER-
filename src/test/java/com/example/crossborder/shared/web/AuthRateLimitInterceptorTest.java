package com.example.crossborder.shared.web;

import com.example.crossborder.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitInterceptorTest {

    @Test
    void allowsRequestsWithinCapacityAndRejectsTheNextRequest() {
        AuthRateLimitInterceptor interceptor = new AuthRateLimitInterceptor(2, Duration.ofMinutes(1));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertThrows(ApiException.class, () -> interceptor.preHandle(request, response, new Object()));
    }
}