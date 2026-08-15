package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStatusServiceTest {
    @TempDir
    Path root;

    @Test
    void reportsExecutionIdentityCapabilitiesCpuAndMemoryWithoutSecrets() {
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setEnabled(true);
        properties.setWorkingDirectory(root.toString());
        properties.setSiteRoot(root.toString());

        Map<String, Object> status = new ServerStatusService(properties).status();

        assertThat(status.get("executionUser")).isInstanceOf(String.class);
        assertThat(status).containsKeys("cpuLoad", "memory", "heapUsedBytes", "heapMaxBytes", "disk", "capabilities");
        if (status.get("cpuLoad") != null) {
            assertThat((Double) status.get("cpuLoad")).isBetween(0.0, 1.0);
        }
        Map<?, ?> memory = (Map<?, ?>) status.get("memory");
        assertThat(memory.containsKey("physicalTotalBytes")).isTrue();
        assertThat(memory.containsKey("physicalFreeBytes")).isTrue();
        assertThat(memory.containsKey("physicalUsedBytes")).isTrue();
        Map<?, ?> capabilities = (Map<?, ?>) status.get("capabilities");
        assertThat(capabilities.get("credentialsExposed")).isEqualTo(false);
        assertThat(capabilities.get("dangerousActionsRequireApproval")).isEqualTo(true);
        if (!"root".equals(status.get("executionUser"))) {
            assertThat(capabilities.get("serviceRestart")).isEqualTo(false);
        }
    }
}
