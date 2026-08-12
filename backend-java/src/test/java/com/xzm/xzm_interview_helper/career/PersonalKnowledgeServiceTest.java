package com.xzm.xzm_interview_helper.career;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalKnowledgeServiceTest {
    @Mock
    private PersonalKnowledgeRepository repository;

    @InjectMocks
    private PersonalKnowledgeService service;

    @Test
    void searchesOnlyDocumentsOwnedByTheRequestedUser() {
        when(repository.loadContents(42)).thenReturn(List.of(
                new PersonalKnowledgeRepository.DocumentContent(
                        9,
                        "我的 MySQL 项目",
                        "DOCUMENT",
                        "使用 MySQL B+Tree 索引与 explain 定位慢查询。"
                )
        ));

        List<PersonalKnowledgeService.Hit> hits = service.search(42, "MySQL 索引为什么使用 B+Tree");

        verify(repository).loadContents(42);
        assertFalse(hits.isEmpty());
        assertEquals(9, hits.get(0).documentId());
        assertEquals("我的 MySQL 项目", hits.get(0).title());
    }

    @Test
    void promptWrapsPrivateDataAsUntrustedAndRequiresCitation() {
        String prompt = service.promptContext(List.of(
                new PersonalKnowledgeService.Hit(1, "候选人简历", "DOCUMENT", "用户明确提交的项目内容", 5.0)
        ));

        assertTrue(prompt.contains("untrusted reference data"));
        assertTrue(prompt.contains("[个人资料：候选人简历]"));
        assertTrue(prompt.contains("<user_private_knowledge>"));
    }
}
