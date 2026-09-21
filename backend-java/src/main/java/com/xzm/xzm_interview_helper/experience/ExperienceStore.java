package com.xzm.xzm_interview_helper.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Small personal corpus: publish one versioned snapshot atomically; learning data is separate. */
@Repository
public class ExperienceStore implements InitializingBean {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final ExperienceProperties config;

  public ExperienceStore(
    JdbcTemplate jdbc,
    ObjectMapper mapper,
    TransactionTemplate tx,
    ExperienceProperties config
  ) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.tx = tx;
    this.config = config;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    jdbc.execute(
      """
      CREATE TABLE IF NOT EXISTS experience_library (
        user_id INT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 1, payload MEDIUMTEXT NOT NULL,
        sync_status VARCHAR(32) NOT NULL DEFAULT 'IDLE', sync_message VARCHAR(600) NOT NULL DEFAULT '',
        sync_started_at DATETIME NULL, synced_at DATETIME NULL,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """
    );
    jdbc.execute(
      """
      CREATE TABLE IF NOT EXISTS experience_revision (
        user_id INT NOT NULL, revision BIGINT NOT NULL, payload MEDIUMTEXT NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id, revision)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """
    );
    jdbc.execute(
      """
      CREATE TABLE IF NOT EXISTS experience_progress (
        user_id INT NOT NULL, question_key VARCHAR(100) NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
        personal_answer MEDIUMTEXT NOT NULL, evidence TEXT NOT NULL, target_role VARCHAR(300) NOT NULL DEFAULT '',
        pinned BOOLEAN NOT NULL DEFAULT FALSE, due_at DATE NULL,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY(user_id, question_key)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """
    );
    jdbc.execute(
      """
      CREATE TABLE IF NOT EXISTS experience_attempt (
        id VARCHAR(36) PRIMARY KEY, user_id INT NOT NULL, question_key VARCHAR(100) NOT NULL,
        revision BIGINT NOT NULL, question TEXT NOT NULL, answer MEDIUMTEXT NOT NULL,
        feedback MEDIUMTEXT NOT NULL, source_snapshot MEDIUMTEXT NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        KEY idx_experience_attempt_user(user_id, created_at)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """
    );
    if (config.getOwnerId() > 0 && !config.getSeedFile().isBlank()) {
      Integer existing = jdbc.queryForObject(
        "SELECT COUNT(*) FROM experience_library WHERE user_id=?",
        Integer.class,
        config.getOwnerId()
      );
      if (existing != null && existing == 0) {
        Path path = Path.of(config.getSeedFile());
        if (Files.size(path) > 12_000_000) throw new IllegalArgumentException(
          "Experience seed exceeds limit"
        );
        ObjectNode seed = (ObjectNode) mapper.readTree(Files.readString(path));
        if (
          !seed.path("docs").isArray() || !seed.path("questions").isArray()
        ) throw new IllegalArgumentException("Invalid experience seed");
        jdbc.update(
          "INSERT IGNORE INTO experience_library(user_id,payload) VALUES (?,?)",
          config.getOwnerId(),
          seed.toString()
        );
      }
    }
  }

  public JdbcTemplate database() {
    return jdbc;
  }

  public record Snapshot(long revision, ObjectNode data) {}

  public Snapshot read(int user) {
    List<Snapshot> rows = jdbc.query(
      "SELECT revision,payload FROM experience_library WHERE user_id=?",
      (rs, n) -> {
        try {
          return new Snapshot(
            rs.getLong(1),
            (ObjectNode) mapper.readTree(rs.getString(2))
          );
        } catch (Exception e) {
          throw new IllegalStateException("Invalid stored library", e);
        }
      },
      user
    );
    if (rows.isEmpty()) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "当前账号尚未接入面经库"
    );
    return rows.get(0);
  }

  public Map<String, Object> status(int user) {
    var rows = jdbc.queryForList(
      "SELECT revision,sync_status AS status,sync_message AS message,sync_started_at AS startedAt,synced_at AS syncedAt FROM experience_library WHERE user_id=?",
      user
    );
    if (rows.isEmpty()) return Map.of(
      "connected",
      false,
      "message",
      "当前账号尚未接入面经库"
    );
    rows.get(0).put("connected", true);
    rows
      .get(0)
      .put(
        "automatic",
        user == config.getOwnerId() && !config.getCookieFile().isBlank()
      );
    return rows.get(0);
  }

  public void publish(int user, long previous, ObjectNode data) {
    if (data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 12_000_000)
      throw new IllegalStateException("面经库超过当前容量上限，本次未发布");
    tx.executeWithoutResult(s -> {
      jdbc.update(
        "INSERT IGNORE INTO experience_revision(user_id,revision,payload) SELECT user_id,revision,payload FROM experience_library WHERE user_id=? AND revision=?",
        user,
        previous
      );
      int updated = jdbc.update(
        "UPDATE experience_library SET payload=?,revision=revision+1,updated_at=NOW() WHERE user_id=? AND revision=?",
        data.toString(),
        user,
        previous
      );
      if (updated != 1) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "资料已更新，请重新同步或保存"
      );
      jdbc.update(
        "INSERT INTO experience_revision(user_id,revision,payload) VALUES (?,?,?)",
        user,
        previous + 1,
        data.toString()
      );
    });
  }

  public List<Map<String, Object>> progress(int user) {
    return jdbc.queryForList(
      "SELECT question_key AS questionKey,state,personal_answer AS personalAnswer,evidence,target_role AS targetRole,pinned,due_at AS dueAt,updated_at AS updatedAt FROM experience_progress WHERE user_id=?",
      user
    );
  }

  public void saveProgress(
    int user,
    String key,
    String state,
    String answer,
    String evidence,
    String role,
    boolean pinned,
    String due
  ) {
    jdbc.update(
      """
      INSERT INTO experience_progress(user_id,question_key,state,personal_answer,evidence,target_role,pinned,due_at)
      VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE state=VALUES(state),personal_answer=VALUES(personal_answer),
      evidence=VALUES(evidence),target_role=VALUES(target_role),pinned=VALUES(pinned),due_at=VALUES(due_at),updated_at=NOW()
      """,
      user,
      key,
      state,
      answer,
      evidence,
      role,
      pinned,
      due
    );
  }

  public List<Map<String, Object>> attempts(int user, String key) {
    Set<String> aliases = relatedKeys(read(user).data(), key);
    String placeholders = String.join(
      ",",
      java.util.Collections.nCopies(aliases.size(), "?")
    );
    var args = new java.util.ArrayList<Object>();
    args.add(user);
    args.addAll(aliases);
    return jdbc.queryForList(
      "SELECT id,question_key AS questionKey,revision,question,answer,feedback,source_snapshot AS sourceSnapshot,created_at AS createdAt FROM experience_attempt WHERE user_id=? AND question_key IN (" +
      placeholders +
      ") ORDER BY created_at DESC LIMIT 30",
      args.toArray()
    );
  }

  public static java.util.Set<String> relatedKeys(ObjectNode data, String key) {
    java.util.Set<String> keys = new java.util.LinkedHashSet<>();
    keys.add(key);
    boolean changed;
    do {
      changed = false;
      for (var q : data.path("questions"))
        if (
          keys.contains(q.path("mergedInto").asText()) &&
          keys.add(q.path("key").asText())
        ) changed = true;
    } while (changed);
    return keys;
  }

  public void attempt(
    int user,
    String id,
    String key,
    long revision,
    String question,
    String answer,
    String feedback,
    String sources
  ) {
    jdbc.update(
      "INSERT INTO experience_attempt(id,user_id,question_key,revision,question,answer,feedback,source_snapshot) VALUES (?,?,?,?,?,?,?,?)",
      id,
      user,
      key,
      revision,
      question,
      answer,
      feedback,
      sources
    );
  }
}
