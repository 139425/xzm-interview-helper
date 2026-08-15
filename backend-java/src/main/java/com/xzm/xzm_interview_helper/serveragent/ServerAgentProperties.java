package com.xzm.xzm_interview_helper.serveragent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-closed limits for the administrator-only server agent.
 *
 * <p>The browser never receives an operating-system credential. Commands run as the Java
 * service account and every mutating or otherwise unknown operation needs a short-lived,
 * single-use approval.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.server-agent")
public class ServerAgentProperties {
    private boolean enabled = false;
    private int maxConcurrentCommands = 2;
    private int maxConcurrentAgents = 1;
    private int commandTimeoutSeconds = 30;
    private int maxOutputChars = 65_536;
    private int maxAgentSteps = 8;
    private int aiTimeoutSeconds = 90;
    private int approvalTtlSeconds = 300;
    private String aiProvider = "deepseek";
    private String aiModel = "deepseek-v4-flash";
    private String siteRoot = "/www/wwwroot";
    private String sitePublicBaseUrl = "/agent-sites";
    private String workingDirectory = "/www/wwwroot";
    private List<String> allowedRoots = new ArrayList<>(List.of(
            "/www/wwwroot",
            "/opt",
            "/var/log",
            "/etc/nginx"
    ));
}
