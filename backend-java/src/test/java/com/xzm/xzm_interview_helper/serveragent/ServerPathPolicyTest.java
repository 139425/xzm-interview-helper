package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerPathPolicyTest {
    @TempDir
    Path root;

    @Test
    void permitsConfiguredRootAndRejectsEscapeOrCredentialFiles() {
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setAllowedRoots(List.of(root.toString()));
        ServerPathPolicy policy = new ServerPathPolicy(properties);

        assertThat(policy.resolve(root.resolve("site/index.html").toString()))
                .isEqualTo(root.resolve("site/index.html").toAbsolutePath().normalize());
        assertThatThrownBy(() -> policy.resolve(root.resolve("../outside.txt").toString()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.resolve(root.resolve(".env").toString()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsAnInRootSymlinkAliasToCredentialFile() throws Exception {
        Path credential = Files.writeString(root.resolve(".env"), "DB_PASSWORD=hidden");
        Path alias = root.resolve("public-config");
        try {
            Files.createSymbolicLink(alias, credential);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are not available: " + exception.getMessage());
        }

        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setAllowedRoots(List.of(root.toString()));
        ServerPathPolicy policy = new ServerPathPolicy(properties);

        assertThatThrownBy(() -> policy.resolve(alias.toString()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
