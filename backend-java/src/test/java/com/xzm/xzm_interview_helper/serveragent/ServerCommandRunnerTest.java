package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCommandRunnerTest {
    @TempDir
    Path root;

    private ServerCommandRunner runner;

    @AfterEach
    void closeRunner() {
        if (runner != null) runner.close();
    }

    @Test
    void capsOutputAndReportsTimeouts() {
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setWorkingDirectory(root.toString());
        properties.setMaxOutputChars(1_024);
        properties.setCommandTimeoutSeconds(5);
        runner = new ServerCommandRunner(properties, new CredentialRedactor(Map.of()));

        String noisy = ServerCommandRunner.isWindows()
                ? "[Console]::Out.Write(('x' * 5000))"
                : "printf '%5000s' x";
        ServerCommandRunner.CommandResult output = runner.runShell(noisy, 5);
        assertThat(output.output().length()).isLessThanOrEqualTo(1_024);
        assertThat(output.truncated()).isTrue();

        String slow = ServerCommandRunner.isWindows() ? "Start-Sleep -Seconds 3" : "sleep 3";
        ServerCommandRunner.CommandResult timeout = runner.runShell(slow, 1);
        assertThat(timeout.timedOut()).isTrue();
        assertThat(timeout.exitCode()).isEqualTo(124);
        assertThat(timeout.output()).contains("timed out");
    }
}
