package com.xzm.xzm_interview_helper.service;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmProblemCatalogServiceTest {

    private AlgorithmProblemCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new AlgorithmProblemCatalogService();
        catalog.loadCatalog();
    }

    @Test
    void containsTheCompleteHot100AndCodeTop100RankRanges() {
        List<AlgorithmProblemSummary> hot100 = catalog.list("HOT100", "", "", false);
        List<AlgorithmProblemSummary> codeTop100 =
                catalog.list("CODETOP100", "", "", false);

        assertEquals(100, hot100.size());
        assertEquals(100, codeTop100.size());
        assertEquals(
                IntStream.rangeClosed(1, 100).boxed().toList(),
                hot100.stream().map(AlgorithmProblemSummary::getHot100Rank).toList()
        );
        assertEquals(
                IntStream.rangeClosed(1, 100).boxed().toList(),
                codeTop100.stream().map(AlgorithmProblemSummary::getCodeTopRank).toList()
        );
    }

    @Test
    void exposesInterfaceJudgeProblemsAcrossAllDifficultyBands() {
        List<AlgorithmProblemSummary> judgeable = catalog.list("", "", "", true);

        assertFalse(judgeable.isEmpty());
        assertTrue(judgeable.stream().allMatch(AlgorithmProblemSummary::isJudgeable));
        assertTrue(judgeable.stream().anyMatch(item -> "EASY".equals(item.getDifficulty())));
        assertTrue(judgeable.stream().anyMatch(item -> "MEDIUM".equals(item.getDifficulty())));
        assertTrue(judgeable.stream().anyMatch(item -> "HARD".equals(item.getDifficulty())));
    }

    @Test
    void assignsTimeLimitsByDifficulty() {
        assertEquals(20, AlgorithmProblemCatalogService.minutesForDifficulty("EASY"));
        assertEquals(30, AlgorithmProblemCatalogService.minutesForDifficulty("MEDIUM"));
        assertEquals(45, AlgorithmProblemCatalogService.minutesForDifficulty("HARD"));
    }
}
