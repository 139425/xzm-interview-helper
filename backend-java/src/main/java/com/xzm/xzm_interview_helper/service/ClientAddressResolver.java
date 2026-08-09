package com.xzm.xzm_interview_helper.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves a rate-limit key without trusting attacker-controlled forwarding headers.
 */
@Component
public class ClientAddressResolver {

    private static final int MAX_FORWARDED_HEADER_CHARS = 1_024;
    private static final int MAX_FORWARDED_HOPS = 16;
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final Set<String> trustedProxyAddresses;

    public ClientAddressResolver(
            @Value("${app.security.trusted-proxy-addresses:}") String trustedProxyAddresses
    ) {
        Set<String> normalized = new HashSet<>();
        Arrays.stream(trustedProxyAddresses == null ? new String[0] : trustedProxyAddresses.split(","))
                .map(ClientAddressResolver::normalizeIpLiteral)
                .filter(value -> value != null)
                .forEach(normalized::add);
        this.trustedProxyAddresses = Set.copyOf(normalized);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeIpLiteral(request.getRemoteAddr());
        if (remoteAddress == null) {
            return "unknown";
        }
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            // X-Forwarded-For is intentionally ignored unless the direct TCP peer is explicitly
            // configured as a trusted reverse proxy.
            return remoteAddress;
        }

        Enumeration<String> forwardedHeaders = request.getHeaders("X-Forwarded-For");
        if (forwardedHeaders == null || !forwardedHeaders.hasMoreElements()) {
            return remoteAddress;
        }
        String forwarded = forwardedHeaders.nextElement();
        if (forwardedHeaders.hasMoreElements()) {
            // Multiple physical header lines are ambiguous and have historically been interpreted
            // differently by proxies and servlet containers. Fail conservative instead.
            return remoteAddress;
        }
        if (forwarded == null
                || forwarded.isBlank()
                || forwarded.length() > MAX_FORWARDED_HEADER_CHARS) {
            return remoteAddress;
        }
        String[] rawHops = forwarded.split(",");
        if (rawHops.length > MAX_FORWARDED_HOPS) {
            return remoteAddress;
        }

        List<String> hops = new ArrayList<>(rawHops.length);
        for (String rawHop : rawHops) {
            String hop = normalizeIpLiteral(rawHop);
            if (hop == null) {
                return remoteAddress;
            }
            hops.add(hop);
        }

        // Walk from the application towards the client. The first untrusted hop is the address
        // the trusted proxy actually observed, so attacker-supplied entries farther left cannot
        // choose an arbitrary rate-limit bucket.
        for (int index = hops.size() - 1; index >= 0; index--) {
            String hop = hops.get(index);
            if (!trustedProxyAddresses.contains(hop)) {
                return hop;
            }
        }
        return remoteAddress;
    }

    private static String normalizeIpLiteral(String rawAddress) {
        if (rawAddress == null) {
            return null;
        }
        String candidate = rawAddress.trim();
        if (candidate.isEmpty() || !IP_LITERAL.matcher(candidate).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
