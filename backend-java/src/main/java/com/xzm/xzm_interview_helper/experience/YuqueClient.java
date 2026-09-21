package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Session is read from a protected file and sent only to the fixed Yuque origin. */
@Component
public class YuqueClient {

  private final ExperienceProperties config;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(15))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();
  private static final Pattern APP = Pattern.compile(
    "window\\.appData\\s*=\\s*JSON\\.parse\\(decodeURIComponent\\(\"(.*?)\"\\)\\)",
    Pattern.DOTALL
  );

  public YuqueClient(ExperienceProperties config, ObjectMapper mapper) {
    this.config = config;
    this.mapper = mapper;
  }

  public String base() {
    if (
      !config.getBook().matches("[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+")
    ) throw new IllegalStateException("语雀知识库未配置");
    return "https://www.yuque.com/" + config.getBook();
  }

  public JsonNode book() throws Exception {
    String body = get(base(), false);
    var match = APP.matcher(body);
    if (!match.find()) throw new IllegalStateException(
      "无法读取语雀目录，请更新登录凭据后重试"
    );
    JsonNode book = mapper
      .readTree(
        URLDecoder.decode(
          match.group(1).replace("+", "%2B"),
          StandardCharsets.UTF_8
        )
      )
      .path("book");
    if (
      !book.path("toc").isArray() || book.path("id").asLong() == 0
    ) throw new IllegalStateException("语雀目录不完整，本次未发布");
    return book;
  }

  public static List<JsonNode> descendants(JsonNode book, long rootId) {
    Map<String, JsonNode> nodes = new LinkedHashMap<>();
    String root = null;
    for (JsonNode n : book.path("toc")) {
      nodes.put(n.path("uuid").asText(), n);
      if (n.path("doc_id").asLong() == rootId) root = n.path("uuid").asText();
    }
    if (root == null) throw new IllegalStateException(
      "未找到配置的秋招目录，本次未发布"
    );
    List<JsonNode> result = new ArrayList<>();
    for (JsonNode n : nodes.values()) {
      String parent = n.path("parent_uuid").asText();
      Set<String> seen = new HashSet<>();
      while (!parent.isBlank() && seen.add(parent)) {
        if (parent.equals(root)) {
          if (n.path("type").asText().equals("DOC")) result.add(n);
          break;
        }
        var p = nodes.get(parent);
        if (p == null) break;
        parent = p.path("parent_uuid").asText();
      }
    }
    if (
      result.isEmpty() || result.size() > 500
    ) throw new IllegalStateException("秋招目录数量异常，本次未发布");
    if (
      result
        .stream()
        .map(n -> n.path("doc_id").asText())
        .distinct()
        .count() !=
      result.size()
    ) throw new IllegalStateException("目录包含重复文档，本次未发布");
    return result;
  }

  private String slug(JsonNode doc) {
    String s = doc.path("url").asText();
    if (!s.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException(
      "无效的语雀文档标识"
    );
    return s;
  }

  public JsonNode detail(JsonNode book, JsonNode doc) throws Exception {
    JsonNode data = mapper
      .readTree(
        get(
          "https://www.yuque.com/api/docs/" +
          slug(doc) +
          "?book_id=" +
          book.path("id").asLong(),
          false
        )
      )
      .path("data");
    if (
      data.path("id").asLong() != doc.path("doc_id").asLong() ||
      !data.path("type").asText().equals("Doc")
    ) throw new IllegalStateException("语雀文档校验失败，本次未发布");
    return data;
  }

  public String markdown(JsonNode doc) throws Exception {
    return get(base() + "/" + slug(doc) + "/markdown?attachment=true", true);
  }

  public String source(JsonNode doc) {
    return base() + "/" + slug(doc);
  }

  private String get(String url, boolean markdown) throws Exception {
    String cookie = Files.readString(Path.of(config.getCookieFile())).strip();
    if (
      cookie.isBlank() || cookie.contains("\n") || cookie.contains("\r")
    ) throw new IllegalStateException("语雀登录凭据无效");
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(30))
      .header("Cookie", cookie)
      .header("User-Agent", "Mozilla/5.0")
      .header("Referer", base())
      .GET()
      .build();
    var response = http.send(
      request,
      HttpResponse.BodyHandlers.ofInputStream()
    );
    try (var input = response.body()) {
      if (response.statusCode() != 200) throw new IllegalStateException(
        "语雀访问失败（HTTP " + response.statusCode() + "），原有数据已保留"
      );
      if (
        markdown &&
        !response
          .headers()
          .firstValue("Content-Type")
          .orElse("")
          .contains("markdown")
      ) throw new IllegalStateException("未取得语雀正文，请检查登录状态");
      byte[] bytes = input.readNBytes(2_000_001);
      if (bytes.length > 2_000_000) throw new IllegalStateException(
        "语雀文档超过读取上限"
      );
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
