package com.xzm.xzm_interview_helper.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.mapper.HelperUserMapper;
import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Validates JWTs and resolves their account against the current database state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String[] WHITE_LIST = {
            "/user/login",
            "/user/register",
            "/user/verification/**",
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/api/recruitments/**",
            "/webjars/**",
            "/favicon.ico"
    };

    private final JwtUtil jwtUtil;
    private final HelperUserMapper helperUserMapper;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getServletPath();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isWhiteListPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Request is missing an authentication token: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证 Token");
            return;
        }

        Claims claims;
        try {
            claims = jwtUtil.validateToken(authHeader.substring(7));
        } catch (ExpiredJwtException e) {
            log.warn("Authentication token has expired: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "认证已过期，请重新登录");
            return;
        } catch (MalformedJwtException e) {
            log.warn("Authentication token is malformed: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 格式无效");
            return;
        } catch (SignatureException e) {
            log.warn("Authentication token signature is invalid: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 验证失败");
            return;
        } catch (Exception e) {
            log.warn("Authentication token validation failed: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 验证失败");
            return;
        }

        Object userIdClaim = claims.get("userId");
        String username = claims.get("username", String.class);
        if (!(userIdClaim instanceof Number userIdNumber)
                || username == null
                || username.isBlank()) {
            log.warn("Authentication token has incomplete claims: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 信息无效");
            return;
        }

        long userId = userIdNumber.longValue();
        if (userId < Integer.MIN_VALUE || userId > Integer.MAX_VALUE) {
            log.warn("Authentication token has an invalid user id: {}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token 信息无效");
            return;
        }

        HelperUser currentUser;
        try {
            /*
             * A valid signature proves who issued the token, not that the account is
             * still active. This lookup makes account deletion revoke outstanding JWTs.
             */
            currentUser = helperUserMapper.selectOne(
                    new QueryWrapper<HelperUser>()
                            .select("user_id", "username", "user_type")
                            .eq("user_id", (int) userId)
                            .eq("username", username)
                            .last("LIMIT 1")
            );
        } catch (Exception e) {
            log.error("Unable to validate account state for userId={}", userId, e);
            sendErrorResponse(
                    response,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "认证服务暂时不可用"
            );
            return;
        }

        if (currentUser == null) {
            log.warn("Token account is no longer active: userId={}", userId);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "账号已失效，请重新登录");
            return;
        }

        request.setAttribute("userId", userId);
        request.setAttribute("username", currentUser.getUsername());
        request.setAttribute("userType", currentUser.getUser_type());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        currentUser.getUsername(),
                        null,
                        java.util.List.of(new SimpleGrantedAuthority(
                                isAdministrator(currentUser.getUser_type()) ? "ROLE_ADMIN" : "ROLE_USER"
                        ))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    public boolean isWhiteListPath(String requestPath) {
        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAdministrator(String userType) {
        if (userType == null) return false;
        String normalized = userType.strip().toUpperCase(Locale.ROOT);
        return "管理员".equals(userType.strip()) || "ADMIN".equals(normalized) || "ROLE_ADMIN".equals(normalized);
    }

    private void sendErrorResponse(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", status);
        errorResponse.put("message", message);
        errorResponse.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    public static String[] getWhiteList() {
        return WHITE_LIST.clone();
    }
}
