package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Year;
import java.util.HexFormat;
import java.util.Locale;

public final class RecruitmentText {
    private RecruitmentText() {
    }

    public static String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    public static String safeHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return "";
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "";
            }
            return uri.normalize().toString();
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    public static String canonicalUrl(String value) {
        String safe = safeHttpUrl(value);
        if (safe.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(safe);
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null
            ).normalize().toString();
        } catch (URISyntaxException ignored) {
            return safe;
        }
    }

    public static String host(String value) {
        try {
            URI uri = new URI(safeHttpUrl(value));
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * A directory row represents one company's recruitment round for one graduate cohort.
     * This key deliberately ignores the discovery URL so an official site, a community post,
     * and an aggregator row can be merged into a single opportunity.
     */
    public static String opportunityKey(RecruitmentCandidate candidate) {
        String company = normalizeCompany(candidate.getCompany());
        String recruitmentType = normalize(candidate.getRecruitmentType());
        String graduates = normalize(candidate.getTargetGraduates());
        String identity = company + "|" + recruitmentType + "|" + graduates;
        if (company.isEmpty() || (recruitmentType.isEmpty() && graduates.isEmpty())) {
            String primaryUrl = canonicalUrl(candidate.getApplyUrl());
            if (primaryUrl.isEmpty()) primaryUrl = canonicalUrl(candidate.getAnnouncementUrl());
            identity += "|" + (primaryUrl.isEmpty() ? normalize(candidate.getTitle()) : primaryUrl);
        }
        return identity;
    }

    public static String fingerprint(RecruitmentCandidate candidate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    opportunityKey(candidate).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public static LocalDate parseDate(String value) {
        String cleaned = clean(value, 32);
        if (!cleaned.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(cleaned);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static LocalDate parseMonthDay(String value) {
        String cleaned = clean(value, 32);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2})[./月](\\d{1,2})")
                .matcher(cleaned);
        if (!matcher.find()) return null;
        try {
            LocalDate date = LocalDate.of(Year.now().getValue(), Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            return date.isAfter(LocalDate.now().plusDays(7)) ? date.minusYears(1) : date;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeCompany(String value) {
        return normalize(value)
                .replaceAll("(集团|股份|有限责任|有限公司|公司)$", "")
                .replaceAll("(招聘官网|校园招聘)$", "");
    }

    private static String normalize(String value) {
        return clean(value, 1000).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
