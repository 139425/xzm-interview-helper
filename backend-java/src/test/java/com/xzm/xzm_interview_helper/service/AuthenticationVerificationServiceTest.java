package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationVerificationServiceTest {

    @Test
    void fallsBackToImageCaptchaWithoutConfiguredMail() {
        AuthenticationVerificationService service = service(false, "");
        assertEquals(
                AuthenticationVerificationService.RegistrationMode.CAPTCHA,
                service.registrationMode()
        );
        AuthenticationVerificationService.CaptchaChallenge captcha =
                service.issueCaptcha("198.51.100.7");
        assertEquals(true, captcha.imageDataUrl().startsWith("data:image/png;base64,"));
    }

    @Test
    void verifiedSliderTokenIsBoundToAddressAndSingleUse() throws Exception {
        AuthenticationVerificationService service = service(false, "");
        AuthenticationVerificationService.SliderChallenge challenge =
                service.issueSlider("198.51.100.8");
        Thread.sleep(challenge.minimumDurationMs() + 20L);
        AuthenticationVerificationService.VerifiedChallenge verified =
                service.verifySlider(challenge.challengeId(), 100, "198.51.100.8");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeSliderToken(verified.verificationToken(), "198.51.100.9")
        );
        service.consumeSliderToken(verified.verificationToken(), "198.51.100.8");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeSliderToken(verified.verificationToken(), "198.51.100.8")
        );
    }

    private AuthenticationVerificationService service(boolean enabled, String from) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        return new AuthenticationVerificationService(
                beans.getBeanProvider(JavaMailSender.class),
                enabled,
                from,
                10
        );
    }
}
