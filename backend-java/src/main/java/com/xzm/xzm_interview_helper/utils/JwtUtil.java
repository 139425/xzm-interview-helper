package com.xzm.xzm_interview_helper.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 * 提供Token的生成、验证和解析功能
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:xzm-interview-helper-jwt-secret-key-must-be-at-least-32-characters}")
    private String secret;

    @Value("${jwt.expiration:259200000}")  // 默认3天，单位毫秒
    private Long expiration;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @param userType 用户类型
     * @return JWT Token字符串
     */
    public String generateToken(Long userId, String username, String userType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("userType", userType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }


    /**
     * 验证Token并返回Claims
     * @param token JWT Token字符串
     * @return Claims对象，包含Token中的所有声明
     * @throws ExpiredJwtException Token过期
     * @throws MalformedJwtException Token格式错误
     * @throws SignatureException 签名验证失败
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从Token中获取用户ID
     * @param token JWT Token字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     * @param token JWT Token字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从Token中获取用户类型
     * @param token JWT Token字符串
     * @return 用户类型
     */
    public String getUserTypeFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("userType", String.class);
    }

    /**
     * 检查Token是否过期
     * @param token JWT Token字符串
     * @return true表示已过期，false表示未过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 获取Token的过期时间
     * @param token JWT Token字符串
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getExpiration();
    }

    /**
     * 获取Token的签发时间
     * @param token JWT Token字符串
     * @return 签发时间
     */
    public Date getIssuedAtFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getIssuedAt();
    }

    /**
     * 获取配置的过期时间（毫秒）
     * @return 过期时间毫秒数
     */
    public Long getExpiration() {
        return expiration;
    }
}
