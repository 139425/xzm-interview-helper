package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExperiencePersistenceTest {
    JdbcTemplate jdbc;ExperienceStore store;ObjectNode corpus;
    @BeforeEach void setup()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);var mapper=new ObjectMapper();store=new ExperienceStore(jdbc,mapper,new TransactionTemplate(new DataSourceTransactionManager(ds)),new ExperienceProperties());
        store.afterPropertiesSet();corpus=(ObjectNode)mapper.readTree("{\"docs\":[],\"questions\":[{\"key\":\"q\"},{\"key\":\"old\",\"mergedInto\":\"q\"}]}");
        jdbc.update("INSERT INTO experience_library(user_id,payload) VALUES(?,?)",7,corpus.toString());
    }
    @Test void persistsAttemptAndItsEvidenceWithoutCrossUserLeak(){
        store.attempt(7,UUID.randomUUID().toString(),"q",1,"问题","回答","{\"status\":\"PENDING\"}","[{\"text\":\"原文\"}]");
        var attempts=store.attempts(7,"q");assertEquals(1,attempts.size());assertEquals("回答",attempts.get(0).get("answer"));assertTrue(attempts.get(0).get("sourceSnapshot").toString().contains("原文"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->store.read(8));assertEquals(false,store.status(8).get("connected"));
    }
    @Test void snapshotPublishIsAtomicAndPreservesBothVersions(){
        ObjectNode changed=corpus.deepCopy();changed.put("marker","new");store.publish(7,1,changed);
        assertEquals(2,store.read(7).revision());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM experience_revision",Integer.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->store.publish(7,1,corpus));assertEquals("new",store.read(7).data().path("marker").asText());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM experience_revision",Integer.class));
    }
    @Test void mergedHistoryAndPersonalNotesSurviveCorpusUpdates(){
        store.attempt(7,UUID.randomUUID().toString(),"old",1,"旧题","旧答案","{}","[]");
        store.saveProgress(7,"old","INDEPENDENT","我的提纲","项目依据","后端",true,"2026-09-22");
        store.publish(7,1,corpus);assertEquals(1,store.attempts(7,"q").size());assertEquals("我的提纲",store.progress(7).get(0).get("personalAnswer"));assertTrue(store.progress(8).isEmpty());
    }
    @Test void progressCanBeUpdatedAndReviewDateCleared(){
        store.saveProgress(7,"q","PROMPTED","草稿","","",true,"2026-09-22");
        store.saveProgress(7,"q","INDEPENDENT","新答案","新依据","SRE",false,null);
        var p=store.progress(7).get(0);assertEquals("新答案",p.get("personalAnswer"));assertNull(p.get("dueAt"));assertEquals(1,store.progress(7).size());
    }
}
