package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Year;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecruitmentText {
    private static final Pattern FULL_DATE = Pattern.compile(
            "(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?"
    );
    private static final Pattern DEADLINE_MONTH_DAY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[-/.月](\\d{1,2})日?(?!\\d)"
    );

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
        return opportunityKey(
                candidate.getCompany(),
                candidate.getTitle(),
                candidate.getRecruitmentType(),
                candidate.getTargetGraduates()
        );
    }

    public static String opportunityKey(String companyValue, String title, String recruitmentTypeValue, String graduatesValue) {
        String company = normalizeCompany(companyValue);
        String recruitmentType = normalizeRecruitmentRound(recruitmentTypeValue + " " + title);
        String graduates = normalizeGraduateRound(graduatesValue);
        String identity = company + "|" + recruitmentType + "|" + graduates;
        if (company.isEmpty() || (recruitmentType.isEmpty() && graduates.isEmpty())) {
            identity += "|" + normalize(title);
        }
        return identity;
    }

    public static boolean isTargetAutumnRecruitment(RecruitmentCandidate candidate, int graduateYear) {
        if (candidate == null) return false;
        String cohort = clean(candidate.getTargetGraduates() + " " + candidate.getTitle(), 1200);
        String campaign = clean(candidate.getRecruitmentType() + " " + candidate.getTitle(), 1200);
        return hasGraduateYear(cohort, graduateYear) && isAutumnCampaign(campaign);
    }

    public static boolean hasGraduateYear(String value, int graduateYear) {
        String normalized = normalize(value);
        String shortYear = String.valueOf(Math.floorMod(graduateYear, 100));
        return normalized.contains(graduateYear + "届")
                || normalized.contains(graduateYear + "年毕业")
                || normalized.contains(shortYear + "届");
    }

    public static boolean isAutumnCampaign(String value) {
        String cleaned = clean(value, 2000);
        boolean explicitAutumn = List.of("秋招", "秋季招聘", "秋季校园招聘", "提前批", "补录")
                .stream().anyMatch(cleaned::contains);
        if (explicitAutumn) return true;
        boolean excluded = List.of("春招", "春季招聘", "实习", "日常招聘", "社会招聘", "社招")
                .stream().anyMatch(cleaned::contains);
        if (excluded) return false;
        return List.of("校园招聘", "校招", "应届生招聘", "毕业生招聘")
                .stream().anyMatch(cleaned::contains);
    }

    public static String autumnRecruitmentType(RecruitmentCandidate candidate) {
        String value = clean(candidate.getRecruitmentType() + " " + candidate.getTitle(), 1200);
        if (value.contains("提前批")) return "秋招提前批";
        if (value.contains("补录")) return "秋招补录";
        return "秋招";
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

    public static LocalDate parseDeadlineDate(String value) {
        return parseDeadlineDate(value, LocalDate.now());
    }

    static LocalDate parseDeadlineDate(String value, LocalDate referenceDate) {
        String cleaned = clean(value, 128);
        Matcher matcher = FULL_DATE.matcher(cleaned);
        try {
            if (matcher.find()) {
                return LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                );
            }
            Matcher monthDay = DEADLINE_MONTH_DAY.matcher(cleaned);
            if (!monthDay.find()) return null;
            LocalDate candidate = LocalDate.of(
                    referenceDate.getYear(),
                    Integer.parseInt(monthDay.group(1)),
                    Integer.parseInt(monthDay.group(2))
            );
            return candidate.isBefore(referenceDate.minusMonths(6)) ? candidate.plusYears(1) : candidate;
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
                .replaceAll("(集团股份有限公司|股份有限公司|有限责任公司|有限公司|集团|股份|公司)+$", "")
                .replaceAll("(招聘官网|校园招聘)$", "");
    }

    private static String normalizeGraduateRound(String value) {
        Matcher matcher = Pattern.compile("(?<!\\d)(20\\d{2}|\\d{2})届").matcher(normalize(value));
        if (!matcher.find()) return normalize(value);
        int year = Integer.parseInt(matcher.group(1));
        return (year < 100 ? 2000 + year : year) + "届";
    }

    private static String normalizeRecruitmentRound(String value) {
        String cleaned = clean(value, 1200);
        if (cleaned.contains("提前批")) return "秋招提前批";
        if (cleaned.contains("补录")) return "秋招补录";
        if (isAutumnCampaign(cleaned)) return "秋招";
        return normalize(cleaned);
    }

    private static String normalize(String value) {
        return clean(value, 1000).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}
