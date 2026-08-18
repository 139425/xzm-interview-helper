package com.xzm.xzm_interview_helper.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Issues short-lived, one-time human-verification challenges for anonymous auth endpoints.
 * State is deliberately bounded and contains no passwords or long-lived user data.
 */
@Service
public class AuthenticationVerificationService {

    private static final long SLIDER_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long SLIDER_MIN_DURATION_MILLIS = 450L;
    private static final long VERIFIED_TOKEN_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long CAPTCHA_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long EMAIL_COOLDOWN_MILLIS = Duration.ofSeconds(60).toMillis();
    private static final int MAX_ENTRIES = 12_000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE
    );

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, SliderState> sliders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExpiringAddress> verifiedTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CaptchaState> captchas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EmailCodeState> emailCodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> emailCooldowns = new ConcurrentHashMap<>();
    private final JavaMailSender mailSender;
    private final boolean emailVerificationEnabled;
    private final String mailFrom;
    private final long emailCodeTtlMillis;

    public AuthenticationVerificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.email-verification-enabled:false}") boolean emailVerificationEnabled,
            @Value("${app.auth.mail-from:}") String mailFrom,
            @Value("${app.auth.verification-code-minutes:10}") long emailCodeMinutes
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
        this.emailVerificationEnabled = emailVerificationEnabled
                && this.mailSender != null
                && StringUtils.hasText(this.mailFrom);
        this.emailCodeTtlMillis = Duration.ofMinutes(Math.max(3, Math.min(emailCodeMinutes, 30))).toMillis();
    }

    public RegistrationMode registrationMode() {
        return emailVerificationEnabled ? RegistrationMode.EMAIL : RegistrationMode.CAPTCHA;
    }

    public SliderChallenge issueSlider(String clientAddress) {
        cleanupExpired();
        ensureCapacity(sliders.size());
        String id = UUID.randomUUID().toString();
        sliders.put(id, new SliderState(normalizeAddress(clientAddress), System.currentTimeMillis()));
        return new SliderChallenge(id, SLIDER_MIN_DURATION_MILLIS, SLIDER_TTL_MILLIS / 1000L);
    }

    public VerifiedChallenge verifySlider(
            String challengeId,
            int sliderValue,
            String clientAddress
    ) {
        if (!StringUtils.hasText(challengeId) || sliderValue != 100) {
            throw new IllegalArgumentException("请将滑块拖动到最右侧");
        }
        SliderState state = sliders.remove(challengeId);
        long now = System.currentTimeMillis();
        if (state == null || now - state.createdAt() > SLIDER_TTL_MILLIS) {
            throw new IllegalArgumentException("验证已过期，请重新完成滑块");
        }
        if (!state.clientAddress().equals(normalizeAddress(clientAddress))) {
            throw new IllegalArgumentException("验证环境已变化，请重试");
        }
        if (now - state.createdAt() < SLIDER_MIN_DURATION_MILLIS) {
            throw new IllegalArgumentException("操作过快，请重新完成滑块");
        }

        ensureCapacity(verifiedTokens.size());
        String token = UUID.randomUUID().toString();
        verifiedTokens.put(token, new ExpiringAddress(
                state.clientAddress(),
                now + VERIFIED_TOKEN_TTL_MILLIS
        ));
        return new VerifiedChallenge(token, VERIFIED_TOKEN_TTL_MILLIS / 1000L);
    }

    public void consumeSliderToken(String token, String clientAddress) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("请先完成人机验证");
        }
        ExpiringAddress state = verifiedTokens.get(token);
        if (state == null || state.expiresAt() < System.currentTimeMillis()) {
            verifiedTokens.remove(token);
            throw new IllegalArgumentException("人机验证已失效，请重新验证");
        }
        if (!state.clientAddress().equals(normalizeAddress(clientAddress))) {
            throw new IllegalArgumentException("验证环境已变化，请重试");
        }
        if (!verifiedTokens.remove(token, state)) {
            throw new IllegalArgumentException("人机验证已使用，请重新验证");
        }
    }

    public CaptchaChallenge issueCaptcha(String clientAddress) {
        cleanupExpired();
        ensureCapacity(captchas.size());
        int left = 1 + random.nextInt(9);
        int right = 1 + random.nextInt(9);
        boolean addition = random.nextBoolean();
        if (!addition && right > left) {
            int swap = left;
            left = right;
            right = swap;
        }
        int answer = addition ? left + right : left - right;
        String expression = left + (addition ? " + " : " - ") + right + " = ?";
        String id = UUID.randomUUID().toString();
        captchas.put(id, new CaptchaState(
                normalizeAddress(clientAddress),
                answer,
                System.currentTimeMillis() + CAPTCHA_TTL_MILLIS,
                3
        ));
        return new CaptchaChallenge(id, renderCaptcha(expression), CAPTCHA_TTL_MILLIS / 1000L);
    }

    public void verifyCaptcha(String captchaId, String answer, String clientAddress) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("请输入图片中的计算结果");
        }
        CaptchaState state = captchas.get(captchaId);
        if (state == null || state.expiresAt() < System.currentTimeMillis()) {
            captchas.remove(captchaId);
            throw new IllegalArgumentException("图片验证码已过期，请刷新");
        }
        if (!state.clientAddress().equals(normalizeAddress(clientAddress))) {
            captchas.remove(captchaId);
            throw new IllegalArgumentException("验证环境已变化，请刷新验证码");
        }

        int supplied;
        try {
            supplied = Integer.parseInt(answer.trim());
        } catch (NumberFormatException exception) {
            supplied = Integer.MIN_VALUE;
        }
        if (supplied != state.answer()) {
            if (state.remainingAttempts() <= 1) {
                captchas.remove(captchaId);
            } else {
                captchas.put(captchaId, new CaptchaState(
                        state.clientAddress(),
                        state.answer(),
                        state.expiresAt(),
                        state.remainingAttempts() - 1
                ));
            }
            throw new IllegalArgumentException("图片验证码不正确");
        }
        captchas.remove(captchaId);
    }

    public EmailCodeReceipt sendRegistrationCode(
            String email,
            String sliderToken,
            String clientAddress
    ) {
        if (registrationMode() != RegistrationMode.EMAIL) {
            throw new IllegalStateException("邮件验证暂未启用，请使用图片验证码");
        }
        String normalizedEmail = normalizeEmail(email);
        consumeSliderToken(sliderToken, clientAddress);

        long now = System.currentTimeMillis();
        Long cooldownUntil = emailCooldowns.get(normalizedEmail);
        if (cooldownUntil != null && cooldownUntil > now) {
            long seconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            throw new IllegalArgumentException("验证码发送过于频繁，请在 " + seconds + " 秒后重试");
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String salt = UUID.randomUUID().toString();
        emailCodes.put(normalizedEmail, new EmailCodeState(
                digest(salt + code),
                salt,
                now + emailCodeTtlMillis,
                5
        ));
        emailCooldowns.put(normalizedEmail, now + EMAIL_COOLDOWN_MILLIS);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(normalizedEmail);
        message.setSubject("XZM 面试助手注册验证码");
        message.setText("你的注册验证码是：" + code + "\n\n验证码在 "
                + (emailCodeTtlMillis / 60_000L) + " 分钟内有效。若非本人操作，请忽略此邮件。");
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            emailCodes.remove(normalizedEmail);
            emailCooldowns.remove(normalizedEmail);
            throw new IllegalStateException("验证码邮件发送失败，请稍后重试", exception);
        }
        return new EmailCodeReceipt(emailCodeTtlMillis / 1000L, EMAIL_COOLDOWN_MILLIS / 1000L);
    }

    public void verifyRegistrationCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("请输入邮箱验证码");
        }
        EmailCodeState state = emailCodes.get(normalizedEmail);
        if (state == null || state.expiresAt() < System.currentTimeMillis()) {
            emailCodes.remove(normalizedEmail);
            throw new IllegalArgumentException("邮箱验证码已过期，请重新发送");
        }
        if (!MessageDigest.isEqual(
                state.codeDigest(),
                digest(state.salt() + code.trim())
        )) {
            if (state.remainingAttempts() <= 1) {
                emailCodes.remove(normalizedEmail);
            } else {
                emailCodes.put(normalizedEmail, new EmailCodeState(
                        state.codeDigest(),
                        state.salt(),
                        state.expiresAt(),
                        state.remainingAttempts() - 1
                ));
            }
            throw new IllegalArgumentException("邮箱验证码不正确");
        }
        emailCodes.remove(normalizedEmail);
    }

    private String renderCaptcha(String expression) {
        try {
            BufferedImage image = new BufferedImage(180, 56, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 247, 252));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int index = 0; index < 10; index++) {
                graphics.setColor(new Color(80 + random.nextInt(120), 100 + random.nextInt(100), 150 + random.nextInt(80), 130));
                graphics.drawLine(random.nextInt(180), random.nextInt(56), random.nextInt(180), random.nextInt(56));
            }
            graphics.setColor(new Color(18, 38, 74));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            graphics.rotate((random.nextDouble() - 0.5D) * 0.06D, 90, 28);
            graphics.drawString(expression, 22, 37);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("生成图片验证码失败", exception);
        }
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成验证码摘要", exception);
        }
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("请输入有效的邮箱地址");
        }
        return normalized;
    }

    private String normalizeAddress(String clientAddress) {
        return StringUtils.hasText(clientAddress) ? clientAddress.trim() : "unknown";
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sliders.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > SLIDER_TTL_MILLIS);
        verifiedTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        emailCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        emailCooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private void ensureCapacity(int size) {
        if (size >= MAX_ENTRIES) {
            cleanupExpired();
            if (size >= MAX_ENTRIES) {
                throw new IllegalStateException("验证服务繁忙，请稍后重试");
            }
        }
    }

    public enum RegistrationMode { EMAIL, CAPTCHA }

    public record SliderChallenge(String challengeId, long minimumDurationMs, long expiresInSeconds) {}
    public record VerifiedChallenge(String verificationToken, long expiresInSeconds) {}
    public record CaptchaChallenge(String captchaId, String imageDataUrl, long expiresInSeconds) {}
    public record EmailCodeReceipt(long expiresInSeconds, long retryAfterSeconds) {}

    private record SliderState(String clientAddress, long createdAt) {}
    private record ExpiringAddress(String clientAddress, long expiresAt) {}
    private record CaptchaState(String clientAddress, int answer, long expiresAt, int remainingAttempts) {}
    private record EmailCodeState(byte[] codeDigest, String salt, long expiresAt, int remainingAttempts) {}
}
