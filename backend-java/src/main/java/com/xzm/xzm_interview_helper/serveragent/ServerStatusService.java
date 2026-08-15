package com.xzm.xzm_interview_helper.serveragent;

import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ServerStatusService {
    private final ServerAgentProperties properties;

    public ServerStatusService(ServerAgentProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> status() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentEnabled", properties.isEnabled());
        result.put("hostname", hostname());
        result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        result.put("architecture", System.getProperty("os.arch"));
        result.put("executionUser", ProcessHandle.current().info().user().orElse(System.getProperty("user.name", "unknown")));
        result.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1_000);
        result.put("processors", runtime.availableProcessors());
        result.put("cpuLoad", cpuLoad());
        result.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        result.put("heapMaxBytes", runtime.maxMemory());
        result.put("memory", physicalMemory());
        result.put("disk", diskStatus());
        result.put("workingDirectory", properties.getWorkingDirectory());
        result.put("siteRoot", properties.getSiteRoot());
        result.put("sitePublicBaseUrl", properties.getSitePublicBaseUrl());
        result.put("capabilities", capabilities());
        result.put("limits", Map.of(
                "commandTimeoutSeconds", properties.getCommandTimeoutSeconds(),
                "maxOutputChars", properties.getMaxOutputChars(),
                "maxConcurrentCommands", properties.getMaxConcurrentCommands(),
                "maxAgentSteps", properties.getMaxAgentSteps()
        ));
        return result;
    }

    private List<Map<String, Object>> diskStatus() {
        return java.util.Arrays.stream(File.listRoots())
                .map(root -> Map.<String, Object>of(
                        "path", root.getAbsolutePath(),
                        "totalBytes", root.getTotalSpace(),
                        "freeBytes", root.getUsableSpace()
                ))
                .toList();
    }

    private Double cpuLoad() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double load = operatingSystem.getCpuLoad();
            if (Double.isFinite(load) && load >= 0) return Math.min(1, load);
        }
        return null;
    }

    private Map<String, Object> physicalMemory() {
        Map<String, Object> memory = new LinkedHashMap<>();
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            long total = operatingSystem.getTotalMemorySize();
            long free = operatingSystem.getFreeMemorySize();
            memory.put("physicalTotalBytes", total);
            memory.put("physicalFreeBytes", free);
            memory.put("physicalUsedBytes", Math.max(0, total - free));
        } else {
            memory.put("physicalTotalBytes", null);
            memory.put("physicalFreeBytes", null);
            memory.put("physicalUsedBytes", null);
        }
        return memory;
    }

    private Map<String, Object> capabilities() {
        Path working = Path.of(properties.getWorkingDirectory()).toAbsolutePath().normalize();
        Path site = Path.of(properties.getSiteRoot()).toAbsolutePath().normalize();
        boolean systemctl = Files.isExecutable(Path.of("/bin/systemctl"))
                || Files.isExecutable(Path.of("/usr/bin/systemctl"));
        String executionUser = ProcessHandle.current().info().user()
                .orElse(System.getProperty("user.name", "unknown"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", true);
        result.put("readFile", true);
        result.put("writeFile", isWritableOrCreatable(working));
        result.put("createSite", isWritableOrCreatable(site));
        result.put("serviceStatus", systemctl);
        // Report conservatively: most non-root service accounts (including production's www user
        // under NoNewPrivileges) cannot mutate systemd state. The command still returns the real
        // OS permission error if a deployment gives a non-root account an explicit policy.
        result.put("serviceRestart", systemctl && "root".equals(executionUser));
        result.put("dangerousActionsRequireApproval", true);
        result.put("credentialsExposed", false);
        return result;
    }

    private boolean isWritableOrCreatable(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current != null && Files.isWritable(current);
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
