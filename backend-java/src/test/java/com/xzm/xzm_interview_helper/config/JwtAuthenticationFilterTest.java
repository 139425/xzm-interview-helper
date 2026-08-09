package com.xzm.xzm_interview_helper.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.mapper.HelperUserMapper;
import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private HelperUserMapper helperUserMapper;
    private JwtAuthenticationFilter filter;
    private Claims claims;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        helperUserMapper = mock(HelperUserMapper.class);
        filter = new JwtAuthenticationFilter(jwtUtil, helperUserMapper, new ObjectMapper());
        claims = mock(Claims.class);
        when(jwtUtil.validateToken("signed-token")).thenReturn(claims);
        when(claims.get("userId")).thenReturn(9518L);
        when(claims.get("username", String.class)).thenReturn("candidate");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyWhenTheTokenAccountStillExists() throws Exception {
        HelperUser activeUser = new HelperUser();
        activeUser.setUser_id(9518);
        activeUser.setUsername("candidate");
        activeUser.setUser_type("普通用户");
        when(helperUserMapper.selectOne(any(Wrapper.class))).thenReturn(activeUser);

        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute("userId")).isEqualTo(9518L);
        assertThat(request.getAttribute("username")).isEqualTo("candidate");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void rejectsAValidlySignedTokenAfterItsAccountWasDeleted() throws Exception {
        when(helperUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("账号已失效");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/algorithm/problems");
        request.setServletPath("/algorithm/problems");
        request.addHeader("Authorization", "Bearer signed-token");
        return request;
    }
}
