package com.xzm.xzm_interview_helper.service;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemDetail;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AlgorithmProblemCatalogService {

    private static final String LEETCODE_GRAPHQL = "https://leetcode.cn/graphql/";
    private static final Set<String> JUDGEABLE = Set.of(
            "two-sum",
            "valid-parentheses",
            "binary-search",
            "maximum-subarray",
            "longest-substring-without-repeating-characters",
            "merge-intervals",
            "minimum-window-substring"
    );
    private static final String DETAIL_QUERY = """
            query questionData($titleSlug: String!) {
              question(titleSlug: $titleSlug) {
                translatedTitle
                translatedContent
                difficulty
                sampleTestCase
                topicTags { translatedName name slug }
                codeSnippets { langSlug code }
              }
            }
            """;

    private final Map<String, AlgorithmProblemSummary> problems = new LinkedHashMap<>();
    private final Map<String, AlgorithmProblemDetail> detailCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    void loadCatalog() {
        ClassPathResource resource = new ClassPathResource("algorithm_catalog.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(this::mergeCatalogLine);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load algorithm catalog", exception);
        }
        long hotCount = problems.values().stream().filter(item -> item.getHot100Rank() != null).count();
        long codeTopCount = problems.values().stream().filter(item -> item.getCodeTopRank() != null).count();
        if (hotCount != 100 || codeTopCount != 100) {
            throw new IllegalStateException(
                    "Algorithm catalog is incomplete: HOT100=" + hotCount + ", CODETOP100=" + codeTopCount);
        }
        log.info("Algorithm catalog loaded: unique={}, hot100={}, codetop100={}",
                problems.size(), hotCount, codeTopCount);
    }

    public List<AlgorithmProblemSummary> list(
            String source,
            String difficulty,
            String keyword,
            boolean judgeableOnly
    ) {
        String normalizedSource = normalize(source);
        String normalizedDifficulty = normalize(difficulty);
        String normalizedKeyword = normalize(keyword);
        return problems.values().stream()
                .filter(item -> normalizedSource.isEmpty()
                        || item.getSources().stream().map(this::normalize).anyMatch(normalizedSource::equals))
                .filter(item -> normalizedDifficulty.isEmpty()
                        || normalize(item.getDifficulty()).equals(normalizedDifficulty))
                .filter(item -> normalizedKeyword.isEmpty()
                        || normalize(item.getTitle()).contains(normalizedKeyword)
                        || normalize(item.getSlug()).contains(normalizedKeyword)
                        || normalize(item.getFrontendId()).contains(normalizedKeyword))
                .filter(item -> !judgeableOnly || item.isJudgeable())
                .sorted(catalogComparator(normalizedSource))
                .map(this::copySummary)
                .toList();
    }

    public AlgorithmProblemSummary requireSummary(String slug) {
        AlgorithmProblemSummary problem = problems.get(normalize(slug));
        if (problem == null) {
            throw new IllegalArgumentException("算法题不存在");
        }
        return copySummary(problem);
    }

    public AlgorithmProblemDetail detail(String slug) {
        AlgorithmProblemSummary summary = requireSummary(slug);
        return detailCache.computeIfAbsent(summary.getSlug(), ignored -> fetchDetail(summary));
    }

    public List<AlgorithmProblemSummary> judgeableByDifficulty(String difficulty) {
        List<AlgorithmProblemSummary> matches = list("", difficulty, "", true);
        return matches.isEmpty() ? list("", "", "", true) : matches;
    }

    private AlgorithmProblemDetail fetchDetail(AlgorithmProblemSummary summary) {
        AlgorithmProblemDetail detail = copyDetailBase(summary);
        detail.setOfficialUrl("https://leetcode.cn/problems/" + summary.getSlug() + "/");
        detail.setContentHtml(
                "<p>题面暂时无法从力扣同步。你仍可通过右上角官方链接查看完整题目。</p>");
        detail.getCodeTemplates().put("java", defaultJavaTemplate(summary.getSlug()));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Referer", detail.getOfficialUrl());
            headers.set("User-Agent", "Mozilla/5.0 XZM-Interview-Helper");
            Map<String, Object> request = Map.of(
                    "operationName", "questionData",
                    "query", DETAIL_QUERY,
                    "variables", Map.of("titleSlug", summary.getSlug())
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    LEETCODE_GRAPHQL,
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            Object dataValue = response.getBody() == null ? null : response.getBody().get("data");
            if (!(dataValue instanceof Map<?, ?> data)
                    || !(data.get("question") instanceof Map<?, ?> question)) {
                return detail;
            }
            detail.setTitle(text(question.get("translatedTitle"), summary.getTitle()));
            detail.setContentHtml(text(question.get("translatedContent"), detail.getContentHtml()));
            detail.setDifficulty(text(question.get("difficulty"), summary.getDifficulty()).toUpperCase(Locale.ROOT));
            detail.setSampleTestCase(text(question.get("sampleTestCase"), ""));

            if (question.get("topicTags") instanceof List<?> tags) {
                detail.setTags(tags.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(tag -> text(tag.get("translatedName"), text(tag.get("name"), "")))
                        .filter(value -> !value.isBlank())
                        .toList());
            }
            if (question.get("codeSnippets") instanceof List<?> snippets) {
                for (Object value : snippets) {
                    if (!(value instanceof Map<?, ?> snippet)) {
                        continue;
                    }
                    String lang = text(snippet.get("langSlug"), "");
                    String code = text(snippet.get("code"), "");
                    if (!lang.isBlank() && !code.isBlank()) {
                        detail.getCodeTemplates().put(lang, code);
                    }
                }
            }
        } catch (RestClientException exception) {
            log.warn("Unable to fetch LeetCode detail for {}: {}", summary.getSlug(),
                    exception.getClass().getSimpleName());
        }
        return detail;
    }

    private void mergeCatalogLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 6) {
            throw new IllegalStateException("Invalid algorithm catalog row: " + line);
        }
        String source = parts[0].trim().toUpperCase(Locale.ROOT);
        int rank = Integer.parseInt(parts[1].trim());
        String slug = normalize(parts[3]);
        AlgorithmProblemSummary item = problems.computeIfAbsent(slug, ignored -> {
            AlgorithmProblemSummary created = new AlgorithmProblemSummary();
            created.setFrontendId(parts[2].trim());
            created.setSlug(slug);
            created.setTitle(parts[4].trim());
            created.setDifficulty(parts[5].trim().toUpperCase(Locale.ROOT));
            created.setJudgeable(JUDGEABLE.contains(slug));
            created.setTimeLimitMinutes(minutesForDifficulty(created.getDifficulty()));
            return created;
        });
        LinkedHashSet<String> sources = new LinkedHashSet<>(item.getSources());
        sources.add(source);
        item.setSources(new ArrayList<>(sources));
        if ("HOT100".equals(source)) {
            item.setHot100Rank(rank);
        } else if ("CODETOP100".equals(source)) {
            item.setCodeTopRank(rank);
        }
    }

    private Comparator<AlgorithmProblemSummary> catalogComparator(String source) {
        if ("hot100".equals(source)) {
            return Comparator.comparing(item -> item.getHot100Rank() == null
                    ? Integer.MAX_VALUE : item.getHot100Rank());
        }
        if ("codetop100".equals(source)) {
            return Comparator.comparing(item -> item.getCodeTopRank() == null
                    ? Integer.MAX_VALUE : item.getCodeTopRank());
        }
        return Comparator.comparing(AlgorithmProblemSummary::getTitle);
    }

    private AlgorithmProblemSummary copySummary(AlgorithmProblemSummary source) {
        AlgorithmProblemSummary copy = new AlgorithmProblemSummary();
        copy.setFrontendId(source.getFrontendId());
        copy.setSlug(source.getSlug());
        copy.setTitle(source.getTitle());
        copy.setDifficulty(source.getDifficulty());
        copy.setSources(new ArrayList<>(source.getSources()));
        copy.setHot100Rank(source.getHot100Rank());
        copy.setCodeTopRank(source.getCodeTopRank());
        copy.setJudgeable(source.isJudgeable());
        copy.setTimeLimitMinutes(source.getTimeLimitMinutes());
        return copy;
    }

    private AlgorithmProblemDetail copyDetailBase(AlgorithmProblemSummary source) {
        AlgorithmProblemDetail detail = new AlgorithmProblemDetail();
        detail.setFrontendId(source.getFrontendId());
        detail.setSlug(source.getSlug());
        detail.setTitle(source.getTitle());
        detail.setDifficulty(source.getDifficulty());
        detail.setSources(new ArrayList<>(source.getSources()));
        detail.setHot100Rank(source.getHot100Rank());
        detail.setCodeTopRank(source.getCodeTopRank());
        detail.setJudgeable(source.isJudgeable());
        detail.setTimeLimitMinutes(source.getTimeLimitMinutes());
        return detail;
    }

    private String defaultJavaTemplate(String slug) {
        return switch (slug) {
            case "two-sum" -> "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        \n    }\n}";
            case "valid-parentheses" -> "class Solution {\n    public boolean isValid(String s) {\n        \n    }\n}";
            case "binary-search" -> "class Solution {\n    public int search(int[] nums, int target) {\n        \n    }\n}";
            case "maximum-subarray" -> "class Solution {\n    public int maxSubArray(int[] nums) {\n        \n    }\n}";
            case "longest-substring-without-repeating-characters" ->
                    "class Solution {\n    public int lengthOfLongestSubstring(String s) {\n        \n    }\n}";
            case "merge-intervals" ->
                    "class Solution {\n    public int[][] merge(int[][] intervals) {\n        \n    }\n}";
            case "minimum-window-substring" ->
                    "class Solution {\n    public String minWindow(String s, String t) {\n        \n    }\n}";
            default -> "class Solution {\n    // 请按照题目要求实现接口\n}";
        };
    }

    public static int minutesForDifficulty(String difficulty) {
        return switch (normalizeStatic(difficulty)) {
            case "hard" -> 45;
            case "medium" -> 30;
            default -> 20;
        };
    }

    private String normalize(String value) {
        return normalizeStatic(value);
    }

    private static String normalizeStatic(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
