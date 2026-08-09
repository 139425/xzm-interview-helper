package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressResolverTest {

    @Test
    void ignoresForwardingHeadersFromUntrustedDirectPeer() {
        ClientAddressResolver resolver = new ClientAddressResolver("");
        MockHttpServletRequest request = request(
                "198.51.100.20",
                "203.0.113.99"
        );

        assertEquals("198.51.100.20", resolver.resolve(request));
    }

    @Test
    void trustedProxyWalksForwardedChainFromRightToLeft() {
        ClientAddressResolver resolver =
                new ClientAddressResolver("127.0.0.1,10.0.0.2");
        MockHttpServletRequest request = request(
                "127.0.0.1",
                "203.0.113.250, 198.51.100.22, 10.0.0.2"
        );

        assertEquals("198.51.100.22", resolver.resolve(request));
    }

    @Test
    void malformedTrustedProxyHeaderFallsBackToDirectPeer() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        MockHttpServletRequest request = request(
                "127.0.0.1",
                "attacker.example"
        );

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    @Test
    void multipleForwardedHeaderLinesFallBackToDirectPeer() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("X-Forwarded-For", "198.51.100.2");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
