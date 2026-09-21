package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExperienceSync {

  private final ExperienceStore store;
  private final YuqueClient yuque;
  private final ExperienceProperties config;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(
    r -> {
      Thread t = new Thread(r, "yuque-sync");
      t.setDaemon(true);
      return t;
    }
  );

  public ExperienceSync(
    ExperienceStore store,
    YuqueClient yuque,
    ExperienceProperties config
  ) {
    this.store = store;
    this.yuque = yuque;
    this.config = config;
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }

  @Scheduled(
    initialDelayString = "${experience.initial-delay-ms:90000}",
    fixedDelayString = "${experience.sync-interval-ms:1800000}"
  )
  public void scheduled() {
    if (config.getOwnerId() > 0 && !config.getCookieFile().isBlank()) try {
      start(config.getOwnerId());
    } catch (Exception ignored) {
      /* Status endpoint exposes failures; never log credentials. */
    }
  }

  public void start(int user) {
    if (
      user != config.getOwnerId() || config.getCookieFile().isBlank()
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "当前账号未配置语雀同步"
    );
    store.read(user);
    if (!running.compareAndSet(false, true)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "同步正在进行"
    );
    executor.submit(() -> {
      try {
        lockedSync(user);
      } finally {
        running.set(false);
      }
    });
  }

  private void lockedSync(int user) {
    store
      .database()
      .execute(
        (ConnectionCallback<Void>) connection -> {
          String name = "xzm-yuque-" + user;
          boolean acquired = false;
          try (var lock = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            lock.setString(1, name);
            try (var result = lock.executeQuery()) {
              acquired = result.next() && result.getInt(1) == 1;
            }
            if (!acquired) return null;
            store
              .database()
              .update(
                "UPDATE experience_library SET sync_status='RUNNING',sync_message='正在检查秋招目录',sync_started_at=NOW() WHERE user_id=?",
                user
              );
            try {
              sync(user);
            } catch (Exception e) {
              String message = e instanceof IllegalStateException
                ? e.getMessage()
                : "同步未完成，请检查网络或登录凭据；原数据已保留";
              store
                .database()
                .update(
                  "UPDATE experience_library SET sync_status='FAILED',sync_message=? WHERE user_id=?",
                  message.substring(0, Math.min(message.length(), 500)),
                  user
                );
            }
          } finally {
            if (acquired) try (
              var release = connection.prepareStatement(
                "SELECT RELEASE_LOCK(?)"
              )
            ) {
              release.setString(1, name);
              release.execute();
            }
          }
          return null;
        }
      );
  }

  void sync(int user) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
    var snapshot = store.read(user);
    ObjectNode data = snapshot.data().deepCopy();
    JsonNode book = yuque.book();
    List<JsonNode> nodes = YuqueClient.descendants(
      book,
      config.getAutumnDocId()
    );
    String manifest = manifest(nodes);
    Map<String, ObjectNode> docs = new LinkedHashMap<>();
    for (JsonNode d : data.path("docs"))
      docs.put(d.path("id").asText(), (ObjectNode) d);
    Set<String> seen = new HashSet<>();
    int added = 0,
      changed = 0,
      archived = 0;
    for (JsonNode node : nodes) {
      if(System.nanoTime()>deadline)throw new IllegalStateException("同步超时，本次未发布，请重试");
      if (
        Thread.currentThread().isInterrupted()
      ) throw new InterruptedException();
      String id = node.path("doc_id").asText();
      seen.add(id);
      JsonNode detail = yuque.detail(book, node);
      String stamp = detail.path("content_updated_at").asText();
      if (stamp.isBlank()) throw new IllegalStateException(
        "语雀正文版本缺失，本次未发布"
      );
      ObjectNode doc = docs.get(id);
      boolean fresh = doc == null;
      if (fresh) {
        doc = data.objectNode();
        doc.put("id", id);
        doc.put("stage", "秋招");
        doc.set("rows", data.arrayNode());
        ((ArrayNode) data.get("docs")).add(doc);
        docs.put(id, doc);
        added++;
      }
      boolean metadataChanged =
        !doc.path("title").asText().equals(node.path("title").asText()) ||
        !doc.path("url").asText().equals(yuque.source(node)) ||
        !doc.path("active").asBoolean(true);
      boolean contentChanged = !stamp.equals(
        doc.path("remoteVersion").asText()
      );
      if (contentChanged) ExperienceCorpus.updateDocument(
        data,
        doc,
        yuque.markdown(node)
      );
      if (!fresh && (contentChanged || metadataChanged)) changed++;
      doc.put("title", node.path("title").asText());
      doc.put("url", yuque.source(node));
      doc.put("stage", "秋招");
      doc.put("remoteVersion", stamp);
      doc.put("active", true);
    }
    // Validate directory completeness again before any publish or archive.
    if (
      !manifest.equals(
        manifest(YuqueClient.descendants(yuque.book(), config.getAutumnDocId()))
      )
    ) throw new IllegalStateException(
      "语雀目录在同步期间发生变化，请重试；原数据已保留"
    );
    for (ObjectNode doc : docs.values())
      if (
        doc.path("stage").asText().equals("秋招") &&
        !seen.contains(doc.path("id").asText()) &&
        doc.path("active").asBoolean(true)
      ) {
        doc.put("active", false);
        archived++;
      }
    if (added + changed + archived > 0) store.publish(
      user,
      snapshot.revision(),
      data
    );
    store
      .database()
      .update(
        "UPDATE experience_library SET sync_status='SUCCESS',sync_message=?,synced_at=NOW() WHERE user_id=?",
        "秋招检查 " +
        nodes.size() +
        " 篇：新增 " +
        added +
        "，更新 " +
        changed +
        "，归档 " +
        archived +
        "；新问法归类可在待核对中修正",
        user
      );
  }

  static String manifest(List<JsonNode> nodes) {
    return nodes
      .stream()
      .map(
        n ->
          n.path("doc_id").asText() +
          ":" +
          n.path("title").asText() +
          ":" +
          n.path("url").asText()
      )
      .sorted()
      .reduce("", (a, b) -> a + "\n" + b);
  }
}
