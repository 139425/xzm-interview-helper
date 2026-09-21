package com.xzm.xzm_interview_helper.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ExperienceSyncTest {

  final ObjectMapper json = new ObjectMapper();
  final ExperienceStore store = mock(ExperienceStore.class);
  final YuqueClient client = mock(YuqueClient.class);
  final ExperienceProperties config = new ExperienceProperties();

  ObjectNode library() throws Exception {
    return (ObjectNode) json.readTree(
      """
      {"docs":[{"id":"11","title":"原题","stage":"秋招","active":true,"remoteVersion":"old","rows":[]},
      {"id":"99","title":"暑期保留","stage":"暑期","active":true,"rows":[]}],"questions":[]}
      """
    );
  }

  com.fasterxml.jackson.databind.JsonNode book() throws Exception {
    return json.readTree(
      """
      {"id":100,"toc":[{"uuid":"root","doc_id":10,"type":"DOC"},{"uuid":"child","parent_uuid":"root","doc_id":11,"type":"DOC","title":"新标题","url":"slug"}]}
      """
    );
  }

  @Test
  void fetchFailureNeverPublishesPartialSnapshot() throws Exception {
    config.setAutumnDocId(10);
    var original = library();
    when(store.read(7)).thenReturn(new ExperienceStore.Snapshot(1, original));
    when(client.book()).thenReturn(book());
    when(client.detail(any(), any())).thenReturn(
      json.readTree("{\"content_updated_at\":\"new\"}")
    );
    when(client.markdown(any())).thenThrow(
      new IllegalStateException("session expired")
    );
    var sync = new ExperienceSync(store, client, config);
    try {
      assertThrows(IllegalStateException.class, () -> sync.sync(7));
      verify(store, never()).publish(anyInt(), anyLong(), any());
      assertEquals("原题", original.path("docs").get(0).path("title").asText());
    } finally {
      sync.close();
    }
  }

  @Test
  void changingDirectoryDuringFetchDoesNotArchiveOrPublish() throws Exception {
    config.setAutumnDocId(10);
    when(store.read(7)).thenReturn(new ExperienceStore.Snapshot(1, library()));
    var second = book().deepCopy();
    ((ObjectNode) second.path("toc").get(1)).put("title", "再次改名");
    when(client.book()).thenReturn(book(), second);
    when(client.detail(any(), any())).thenReturn(
      json.readTree("{\"content_updated_at\":\"new\"}")
    );
    when(client.markdown(any())).thenReturn("1. 新题");
    when(client.source(any())).thenReturn("https://www.yuque.com/a/b/slug");
    var sync = new ExperienceSync(store, client, config);
    try {
      assertThrows(IllegalStateException.class, () -> sync.sync(7));
      verify(store, never()).publish(anyInt(), anyLong(), any());
    } finally {
      sync.close();
    }
  }

  @Test
  void differentUserCannotTriggerSharedCredentialSync() {
    config.setOwnerId(7);
    config.setCookieFile("/private/cookie");
    var sync = new ExperienceSync(store, client, config);
    try {
      assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> sync.start(8)
      );
      verifyNoInteractions(client, store);
    } finally {
      sync.close();
    }
  }
}
