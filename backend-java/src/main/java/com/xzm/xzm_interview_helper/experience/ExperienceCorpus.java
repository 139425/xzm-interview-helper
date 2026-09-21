package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Deterministic extraction and statistics. AI never changes observed frequencies. */
public final class ExperienceCorpus {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ExperienceCorpus() {}

  public static String hash(String s) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          s.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String normalized(String s) {
    return s
      .replaceAll("</?(?:span|strong|em|p|div|h[1-6])(?:\\s[^>]*)?>", " ")
      .replaceAll("^\\s*(?:[-+*]|[0-9]+[.、)．])\\s*", "")
      .replace("**", "").replace("`", "")
      .replaceAll("\\s+", "")
      .replaceAll("[？?。！!]+$", "")
      .toLowerCase(Locale.ROOT);
  }

  public static String category(String s) {
    String t = s.toLowerCase(Locale.ROOT);
    String[][] rules = {
      { "反问与交流", "反问" },
      {
        "数据库与中间件",
        "mysql",
        "sql",
        "redis",
        "缓存",
        "索引",
        "mq",
        "幂等",
        "事务",
      },
      {
        "AI / RAG",
        "rag",
        "mcp",
        "agent",
        "大模型",
        "幻觉",
        "提示词",
        "ai",
        "skill",
        "向量",
      },
      {
        "Java / JVM / Spring",
        "java",
        "jvm",
        "spring",
        "线程池",
        "集合",
        "hashmap",
        "gc",
      },
      {
        "工程 / SRE / 容器",
        "sre",
        "运维",
        "告警",
        "排障",
        "容器",
        "docker",
        "k8s",
        "部署",
      },
      { "算法与数据结构", "手撕", "算法", "链表", "二叉树", "二分", "排序" },
      {
        "计算机基础",
        "网络",
        "tcp",
        "dns",
        "http",
        "进程",
        "线程",
        "内存",
        "linux",
      },
      { "HR 与行为面", "沟通", "抗压", "学习", "求助", "加班", "职业", "薪资" },
      { "项目与业务", "实习", "项目", "负责", "业务" },
    };
    for (var r : rules)
      for (int i = 1; i < r.length; i++) if (t.contains(r[i])) return r[0];
    return "待核对";
  }

  public static List<JsonNode> questions(ObjectNode data) {
    Map<String, ObjectNode> out = new LinkedHashMap<>();
    for (JsonNode q : data.path("questions")) {
      ObjectNode copy = q.deepCopy();
      copy.put("frequency", 0);
      copy.put("mentions", 0);
      copy.set("evidence", JSON.createArrayNode());
      out.put(q.path("key").asText(), copy);
    }
    Map<String, Set<String>> documents = new HashMap<>();
    for (JsonNode d : data.path("docs"))
      if (d.path("active").asBoolean(true)) for (JsonNode row : d.path("rows"))
        for (JsonNode key : row.path("keys")) {
          var q = out.get(key.asText());
          if (q == null) continue;
          Set<String> seen = documents.computeIfAbsent(key.asText(), k ->
            new HashSet<>()
          );
          seen.add(d.path("id").asText());
          q.put("frequency", seen.size());
          q.put("mentions", q.path("mentions").asInt() + 1);
          ObjectNode evidence = ((ArrayNode) q.get("evidence")).addObject();
          evidence.put("docId", d.path("id").asText());
          evidence.put("title", d.path("title").asText());
          evidence.put("stage", d.path("stage").asText());
          evidence.put("url", d.path("url").asText());
          evidence.set("row", row);
          evidence.put("hash", d.path("hash").asText());
        }
    return out
      .values()
      .stream()
      .filter(q -> q.path("frequency").asInt() > 0)
      .sorted(
        Comparator.<ObjectNode>comparingInt(q -> q.path("frequency").asInt())
          .reversed()
          .thenComparing(q -> q.path("key").asText())
      )
      .map(q -> (JsonNode) q)
      .toList();
  }

  public static void updateDocument(
    ObjectNode data,
    ObjectNode doc,
    String body
  ) {
    Map<String, ArrayDeque<JsonNode>> previous = new HashMap<>();
    for (JsonNode row : doc.path("rows"))
      previous
        .computeIfAbsent(normalized(row.path("text").asText()), k ->
          new ArrayDeque<>()
        )
        .add(row);
    Map<String, JsonNode> known = new HashMap<>();
    for (JsonNode d : data.path("docs"))
      for (JsonNode row : d.path("rows"))
        if (row.path("keys").size() > 0) known.putIfAbsent(
          normalized(row.path("text").asText()),
          row.path("keys")
        );
    Set<String> questionKeys = new HashSet<>();
    for (JsonNode q : data.path("questions"))
      questionKeys.add(q.path("key").asText());
    ArrayNode rows = JSON.createArrayNode();
    String[] lines = body.split("\\R", -1);
    Map<String, Integer> occurrences = new HashMap<>();
    for (int i = 0; i < lines.length; i++) {
      String text = lines[i].strip();
      if (text.isBlank()) continue;
      String norm = normalized(text);
      if (norm.isBlank()) continue;
      int occurrence = occurrences.merge(norm, 1, Integer::sum);
      var queue = previous.get(norm);
      ObjectNode row;
      if (queue != null && !queue.isEmpty()) row = queue.remove().deepCopy();
      else {
        row = JSON.createObjectNode();
        String id =
          doc.path("id").asText() +
          "-" +
          hash(norm).substring(0, 20) +
          "-" +
          occurrence;
        row.put("id", id);
        ArrayNode keys = JSON.createArrayNode();
        boolean structural =
          text.matches(
            "^(#{1,6}\\s.*|!\\[.*|\\[.*]\\(.*|https?://.*|---+|\\*\\*\\*+)$"
          ) ||
          text.matches("^[0-9]+[.、]$");
        if (known.containsKey(norm)) keys = (ArrayNode) known
          .get(norm)
          .deepCopy();
        else if (!structural) {
          String key = "new_" + hash(norm).substring(0, 24);
          keys.add(key);
          if (questionKeys.add(key)) {
            ObjectNode q = ((ArrayNode) data.get("questions")).addObject();
            q.put("key", key);
            q.put("question", text.replaceFirst("^\\s*[0-9]+[.、)]\\s*", ""));
            q.put("category", category(text));
            q.put("group", category(text));
            q.put("reviewed", false);
          }
        }
        row.set("keys", keys);
        if (structural) row.put("excluded", "标题、资源或结构行");
      }
      row.put("text", text);
      row.put("line", i + 1);
      rows.add(row);
    }
    doc.set("rows", rows);
    doc.put("body", body);
    doc.put("hash", hash(body));
  }

  public static JsonNode question(ObjectNode data, String key) {
    return questions(data)
      .stream()
      .filter(q -> q.path("key").asText().equals(key))
      .findFirst()
      .orElseThrow(() ->
        new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "题目不存在或已归档"
        )
      );
  }
}
