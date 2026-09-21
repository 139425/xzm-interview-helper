package com.xzm.xzm_interview_helper.experience;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExperienceCorpusTest {
  @Test void matchingPreservesOperatorsAndLanguageNames() {
    assertNotEquals(ExperienceCorpus.normalized("C++如何工作"), ExperienceCorpus.normalized("C#如何工作"));
    assertNotEquals(ExperienceCorpus.normalized("x < 5 时怎样"), ExperienceCorpus.normalized("x > 5 时怎样"));
  }

  final ObjectMapper mapper = new ObjectMapper();

  ObjectNode corpus() throws Exception {
    return (ObjectNode) mapper.readTree(
      """
      {"questions":[{"key":"sql","question":"SQL优化","category":"数据库","reviewed":true}],
       "docs":[{"id":"1","title":"一面","stage":"秋招","active":true,"rows":[
       {"id":"a","text":"1. SQL优化？","line":1,"keys":["sql"]},
       {"id":"b","text":"2. 如何验证结果？","line":2,"keys":["sql"]}]},
       {"id":"2","title":"二面","stage":"暑期","active":true,"rows":[
       {"id":"c","text":"SQL优化？","line":1,"keys":["sql"]}]}],"chains":[]}
      """
    );
  }

  @Test
  void countsDistinctDocumentsRatherThanFollowupLines() throws Exception {
    var q = ExperienceCorpus.questions(corpus()).get(0);
    assertEquals(2, q.path("frequency").asInt());
    assertEquals(3, q.path("mentions").asInt());
  }

  @Test
  void archivedSourcesDisappearFromStatisticsWithoutDeletingEvidence()
    throws Exception {
    var c = corpus();
    ((ObjectNode) c.path("docs").get(0)).put("active", false);
    assertEquals(
      1,
      ExperienceCorpus.questions(c).get(0).path("frequency").asInt()
    );
    assertEquals(2, c.path("docs").get(0).path("rows").size());
  }

  @Test
  void repeatedImportPreservesManualKeysAndStableRowIdentity()
    throws Exception {
    var c = corpus();
    var d = (ObjectNode) c.path("docs").get(0);
    String body =
      "# 一面\n\n1. SQL优化？\n2. 如何验证结果？\n3. DNS的递归解析？";
    ExperienceCorpus.updateDocument(c, d, body);
    String first = c.toString();
    ExperienceCorpus.updateDocument(c, d, body);
    assertEquals(first, c.toString());
    assertEquals("a", d.path("rows").get(1).path("id").asText());
    assertEquals("sql", d.path("rows").get(2).path("keys").get(0).asText());
    assertEquals(2, c.path("questions").size());
    assertFalse(c.path("questions").get(1).path("reviewed").asBoolean());
  }

  @Test
  void structuralLinesDoNotInflateFrequency() throws Exception {
    var c = corpus();
    var d = (ObjectNode) c.path("docs").get(0);
    ExperienceCorpus.updateDocument(
      c,
      d,
      "# 标题\n---\n![图片](https://example.test/a.png)\n[录音](https://example.test/a.mp3)\n1. SQL优化？"
    );
    assertEquals(1, c.path("questions").size());
    assertEquals(
      2,
      ExperienceCorpus.questions(c).get(0).path("frequency").asInt()
    );
  }

  @Test
  void deletionOnlyRemovesCurrentOccurrence() throws Exception {
    var c = corpus();
    ExperienceCorpus.updateDocument(
      c,
      (ObjectNode) c.path("docs").get(0),
      "# 空记录"
    );
    assertEquals(
      1,
      ExperienceCorpus.questions(c).get(0).path("frequency").asInt()
    );
  }

  @Test
  void duplicateNewRowsHaveDistinctStableIds() throws Exception {
    var c = corpus();
    var d = (ObjectNode) c.path("docs").get(0);
    ExperienceCorpus.updateDocument(c, d, "新题");
    String first = d.path("rows").get(0).path("id").asText();
    ExperienceCorpus.updateDocument(c, d, "新题\n新题");
    assertEquals(first, d.path("rows").get(0).path("id").asText());
    assertNotEquals(first, d.path("rows").get(1).path("id").asText());
  }

  @Test
  void directoryScopeIncludesNestedAutumnOnly() throws Exception {
    var book = mapper.readTree(
      """
      {"toc":[{"uuid":"a","doc_id":10,"type":"DOC"},{"uuid":"b","doc_id":11,"type":"DOC","parent_uuid":"a"},
      {"uuid":"c","doc_id":12,"type":"DOC","parent_uuid":"b"},{"uuid":"other","doc_id":20,"type":"DOC"}]}
      """
    );
    assertEquals(
      List.of(11L, 12L),
      YuqueClient.descendants(book, 10)
        .stream()
        .map(n -> n.path("doc_id").asLong())
        .toList()
    );
    assertThrows(IllegalStateException.class, () ->
      YuqueClient.descendants(book, 99)
    );
  }

  @Test
  void unsafeBookPathsAreRejectedBeforeCredentialsAreRead() {
    var config = new ExperienceProperties();
    config.setBook("evil.test/../../login");
    assertThrows(IllegalStateException.class, () ->
      new YuqueClient(config, mapper).base()
    );
  }
}
