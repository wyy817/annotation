package com.wsi.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.common.MessageUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private JwtUtil jwtUtil;
    private MessageUtil messageUtil;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        messageUtil = mock(MessageUtil.class);
        interceptor = new AuthInterceptor(jwtUtil, new ObjectMapper(), messageUtil);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void allowsCredentialExchangeWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatesCurrentUserEndpoint() throws Exception {
        String token = jwtUtil.generate(42L, "pathologist", "ANNOTATOR");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserContext.get()).isEqualTo(
                new UserContext.UserInfo(42L, "pathologist", "ANNOTATOR")
        );
    }

    @Test
    void rejectsCurrentUserEndpointWithoutToken() throws Exception {
        when(messageUtil.msg("error.auth.unauthorized")).thenReturn("Unauthorized");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void allowsCorsPreflightWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
