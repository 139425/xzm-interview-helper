package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/experience")
public class ExperienceController {

  private final ExperienceStore store;
  private final ExperienceSync sync;
  private final ExperienceAi ai;
  private final ObjectMapper mapper;

  public ExperienceController(
    ExperienceStore store,
    ExperienceSync sync,
    ExperienceAi ai,
    ObjectMapper mapper
  ) {
    this.store = store;
    this.sync = sync;
    this.ai = ai;
    this.mapper = mapper;
  }

  private Map<String, Object> ok(Object data) {
    return Map.of("code", 200, "data", data, "message", "Success");
  }

  @GetMapping("/status")
  public Object status(HttpServletRequest r) {
    return ok(store.status(AuthenticatedUser.id(r)));
  }

  @PostMapping("/sync")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Object sync(HttpServletRequest r) {
    sync.start(AuthenticatedUser.id(r));
    return ok(Map.of("accepted", true));
  }

  @GetMapping("/revisions")
  public Object revisions(HttpServletRequest r) {
    int user = AuthenticatedUser.id(r);
    store.read(user);
    return ok(
      store
        .database()
        .queryForList(
          "SELECT revision,created_at AS createdAt FROM experience_revision WHERE user_id=? ORDER BY revision DESC LIMIT 50",
          user
        )
    );
  }

  @PostMapping("/revisions/{version}/restore")
  public Object restore(
    @PathVariable long version,
    @RequestBody JsonNode body,
    HttpServletRequest r
  ) {
    int user = AuthenticatedUser.id(r);
    var current = store.read(user);
    if (
      body.path("revision").asLong() != current.revision()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "资料已更新，请刷新后重试"
    );
    var rows = store
      .database()
      .queryForList(
        "SELECT payload FROM experience_revision WHERE user_id=? AND revision=?",
        user,
        version
      );
    if (rows.isEmpty()) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "历史版本不存在"
    );
    try {
      store.publish(
        user,
        current.revision(),
        (ObjectNode) mapper.readTree(rows.get(0).get("payload").toString())
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored revision");
    }
    return ok(Map.of("saved", true));
  }

  @GetMapping
  public Object library(HttpServletRequest r) {
    int user = AuthenticatedUser.id(r);
    var snap = store.read(user);
    ObjectNode data = snap.data();
    var docs = mapper.createArrayNode();
    for (JsonNode d : data.path("docs")) {
      ObjectNode item = d.deepCopy();
      item.remove(List.of("body", "rows"));
      item.put("rowCount", d.path("rows").size());
      docs.add(item);
    }
    return ok(
      Map.of(
        "revision",
        snap.revision(),
        "docs",
        docs,
        "questions",
        ExperienceCorpus.questions(data),
        "chains",
        data.path("chains"),
        "progress",
        store.progress(user),
        "status",
        store.status(user)
      )
    );
  }

  @GetMapping("/documents/{id}")
  public Object document(@PathVariable String id, HttpServletRequest r) {
    var data = store.read(AuthenticatedUser.id(r)).data();
    for (JsonNode doc : data.path("docs"))
      if (doc.path("id").asText().equals(id)) return ok(doc);
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "面经不存在");
  }

  @GetMapping("/questions/{key}")
  public Object question(@PathVariable String key, HttpServletRequest r) {
    int user = AuthenticatedUser.id(r);
    var data = store.read(user).data();
    var q = ExperienceCorpus.question(data, key);
    var aliases = ExperienceStore.relatedKeys(data, key);
    return ok(
      Map.of(
        "question",
        q,
        "attempts",
        store.attempts(user, key),
        "relatedNotes",
        store
          .progress(user)
          .stream()
          .filter(
            p ->
              aliases.contains(p.get("questionKey")) &&
              !key.equals(p.get("questionKey"))
          )
          .toList()
      )
    );
  }

  @PutMapping("/questions/{key}/progress")
  public Object progress(
    @PathVariable String key,
    @RequestBody JsonNode body,
    HttpServletRequest r
  ) {
    int user = AuthenticatedUser.id(r);
    ExperienceCorpus.question(store.read(user).data(), key);
    String state = body.path("state").asText("UNKNOWN");
    if (
      !Set.of(
        "UNKNOWN",
        "PROMPTED",
        "INDEPENDENT",
        "TRANSFER",
        "RETESTED"
      ).contains(state)
    ) throw bad("无效的掌握状态");
    String due = body.path("dueAt").asText("");
    try {
      if (!due.isBlank()) LocalDate.parse(due);
    } catch (Exception e) {
      throw bad("复测日期格式不正确");
    }
    store.saveProgress(
      user,
      key,
      state,
      text(body, "personalAnswer", 30000),
      text(body, "evidence", 10000),
      text(body, "targetRole", 300),
      body.path("pinned").asBoolean(),
      due.isBlank() ? null : due
    );
    return ok(Map.of("saved", true));
  }

  @PostMapping("/questions/{key}/review")
  public Object review(
    @PathVariable String key,
    @RequestBody JsonNode body,
    HttpServletRequest r
  ) {
    int user = AuthenticatedUser.id(r);
    var snap = store.read(user);
    ObjectNode data = snap.data().deepCopy();
    ExperienceCorpus.question(data, key);
    if (
      body.path("revision").asLong() != snap.revision()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "题库已更新，请刷新后核对"
    );
    String target = body.path("mergeInto").asText();
    if (body.path("exclude").asBoolean()) {
      for (JsonNode doc : data.path("docs"))
        for (JsonNode row : doc.path("rows")) {
          ArrayNode keys = mapper.createArrayNode();
          for (JsonNode k : row.path("keys"))
            if (!k.asText().equals(key)) keys.add(k);
          if (keys.size() != row.path("keys").size()) {
            ((ObjectNode) row).set("keys", keys);
            ((ObjectNode) row).put("excluded", "人工确认：不计题频");
          }
        }
    } else if (!target.isBlank() && !target.equals(key)) {
      ExperienceCorpus.question(data, target);
      // Preserve historical attempts and personal answers under their original keys.
      for (JsonNode doc : data.path("docs"))
        for (JsonNode row : doc.path("rows")) {
          LinkedHashSet<String> keys = new LinkedHashSet<>();
          for (JsonNode k : row.path("keys"))
            keys.add(k.asText().equals(key) ? target : k.asText());
          var updated = mapper.createArrayNode();
          keys.forEach(updated::add);
          ((ObjectNode) row).set("keys", updated);
        }
      for (JsonNode q : data.path("questions"))
        if (q.path("key").asText().equals(key)) {
          ((ObjectNode) q).put("mergedInto", target);
        }
    } else {
      for (JsonNode q : data.path("questions"))
        if (q.path("key").asText().equals(key)) {
          String title = text(body, "question", 3000),
            category = text(body, "category", 100);
          if (title.isBlank() || category.isBlank()) throw bad(
            "题干与分类不能为空"
          );
          ((ObjectNode) q).put("question", title);
          ((ObjectNode) q).put("category", category);
          ((ObjectNode) q).put("group", category);
          ((ObjectNode) q).put("reviewed", true);
        }
    }
    store.publish(user, snap.revision(), data);
    return ok(Map.of("saved", true));
  }

  @PostMapping("/questions/{key}/attempts")
  public Object attempt(
    @PathVariable String key,
    @RequestBody JsonNode body,
    HttpServletRequest r
  ) {
    int user = AuthenticatedUser.id(r);
    var snap = store.read(user);
    JsonNode q = ExperienceCorpus.question(snap.data(), key);
    String id = text(body, "id", 36);
    try {
      UUID.fromString(id);
    } catch (Exception e) {
      throw bad("缺少有效的作答标识");
    }
    String answer = text(body, "answer", 12000);
    if (answer.isBlank()) throw bad("请先作答");
    String asked = q.path("question").asText();
    String parent = text(body, "parentAttemptId", 36);
    if (!parent.isBlank()) {
      var rows = store
        .database()
        .queryForList(
          "SELECT feedback FROM experience_attempt WHERE id=? AND user_id=? AND question_key=?",
          parent,
          user,
          key
        );
      if (rows.isEmpty()) throw bad("追问来源不存在");
      try {
        asked = mapper
          .readTree(rows.get(0).get("feedback").toString())
          .path("followUp")
          .asText();
      } catch (Exception e) {
        throw bad("追问尚未生成");
      }
      if (asked.isBlank()) throw bad("追问尚未生成");
    } else if (!body.path("rowId").asText().isBlank()) {
      String rowId = text(body, "rowId", 100);
      boolean found = false;
      for (JsonNode e : q.path("evidence"))
        if (e.path("row").path("id").asText().equals(rowId)) {
          asked = e.path("row").path("text").asText();
          found = true;
          break;
        }
      if (!found) throw bad("原始问法已更新，请刷新");
    }
    var old = store
      .database()
      .queryForList(
        "SELECT question_key,answer,question,feedback FROM experience_attempt WHERE id=? AND user_id=?",
        id,
        user
      );
    if (!old.isEmpty()) {
      var previous = old.get(0);
      if (
        !previous.get("answer").equals(answer) ||
        !previous.get("question_key").equals(key) ||
        !previous.get("question").equals(asked)
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "作答标识已用于其他内容"
      );
      try {
        JsonNode f = mapper.readTree(previous.get("feedback").toString());
        if (f.path("status").asText().equals("COMPLETE")) return ok(
          Map.of("id", id, "result", f)
        );
      } catch (Exception ignored) {}
    } else {
      try {
        store.attempt(
          user,
          id,
          key,
          snap.revision(),
          asked,
          answer,
          "{\"status\":\"PENDING\"}",
          q.path("evidence").toString()
        );
      } catch (org.springframework.dao.DuplicateKeyException e) {
        throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "作答已提交，请刷新记录"
        );
      }
    }
    try {
      ObjectNode result = ai.evaluate(user, snap.data(), q, asked, answer);
      store
        .database()
        .update(
          "UPDATE experience_attempt SET feedback=? WHERE id=? AND user_id=?",
          result.toString(),
          id,
          user
        );
      return ok(Map.of("id", id, "result", result));
    } catch (ResponseStatusException e) {
      store
        .database()
        .update(
          "UPDATE experience_attempt SET feedback=? WHERE id=? AND user_id=? AND feedback NOT LIKE '%\"COMPLETE\"%'",
          "{\"status\":\"FAILED\",\"feedback\":\"AI反馈未完成，原始回答已保存\"}",
          id,
          user
        );
      throw e;
    }
  }

  private String text(JsonNode body, String field, int limit) {
    String value = body.path(field).asText("").strip();
    if (value.length() > limit) throw bad(field + " 内容过长");
    return value;
  }

  private ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }
}
