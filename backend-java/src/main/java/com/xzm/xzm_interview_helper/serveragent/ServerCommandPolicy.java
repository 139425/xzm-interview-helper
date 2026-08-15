package com.xzm.xzm_interview_helper.serveragent;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Classifies shell input before it reaches an operating-system process. */
@Component
public class ServerCommandPolicy {
    private static final Pattern CREDENTIAL_ACCESS = Pattern.compile(
            "(?i)(?:cat|tac|less|more|head|tail|grep|awk|sed|type|get-content|copy|cp|base64|xxd|printenv|env|set)"
                    + ".*(?:\\.ssh|id_rsa|id_ed25519|\\.env(?:\\s|$)|credentials?|secrets?|password|passwd|api[_-]?key|token|private[_-]?key)"
    );
    private static final Pattern CREDENTIAL_PATH = Pattern.compile(
            "(?i)(?:/root/\\.ssh|/home/[^/]+/\\.ssh|\\.aws/credentials|\\.kube/config|/proc(?:/|$)|/sys(?:/|$)|"
                    + "/etc/(?:shadow|gshadow|sudoers)(?:\\s|$))"
    );
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)(?:^|[;&|\\s])(?:rm|rmdir|mv|chmod|chown|useradd|userdel|groupadd|groupdel|passwd|"
                    + "shutdown|reboot|halt|poweroff|mkfs|fdisk|parted|dd|mount|umount|iptables|nft|ufw|"
                    + "apt|apt-get|yum|dnf|pacman|pip|npm|docker|podman|kubectl|helm|mysql|psql)"
                    + "(?:\\s|$)|(?:systemctl|service)\\s+(?:start|stop|restart|reload|enable|disable|mask|unmask)"
                    + "|(?:^|\\s)(?:>|>>|tee\\s)|curl.+(?:-X\\s*(?:POST|PUT|PATCH|DELETE)|--data|--upload-file)|wget.+-O"
                    + "|find\\s+.*(?:-delete|-exec|-execdir|-ok)(?:\\s|$)|journalctl\\s+.*--(?:vacuum|rotate|flush|sync)"
    );
    private static final Set<String> READ_ONLY_PREFIXES = Set.of(
            "pwd", "ls", "dir", "whoami", "id", "uptime", "df", "du", "free",
            "vmstat", "iostat", "netstat", "uname", "stat", "journalctl", "systemctl status",
            "systemctl is-active", "systemctl is-enabled", "service --status-all"
    );

    public ServerRisk classify(String command) {
        String normalized = command == null ? "" : command.strip();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) {
            return ServerRisk.BLOCKED;
        }
        if (CREDENTIAL_ACCESS.matcher(normalized).find() || CREDENTIAL_PATH.matcher(normalized).find()) {
            return ServerRisk.BLOCKED;
        }
        if (DANGEROUS.matcher(normalized).find()) {
            return ServerRisk.DANGEROUS;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean readOnly = READ_ONLY_PREFIXES.stream().anyMatch(prefix -> prefix.endsWith(" ")
                ? lower.startsWith(prefix)
                : lower.equals(prefix) || lower.startsWith(prefix + " "));
        // Shell metacharacters can turn an otherwise read-only prefix into a mutation.
        if (readOnly && !containsControlOperator(lower)) {
            return ServerRisk.READ_ONLY;
        }
        // Unknown commands remain possible, but only after an exact-action approval.
        return ServerRisk.DANGEROUS;
    }

    private boolean containsControlOperator(String command) {
        return command.contains(";")
                || command.contains("\n")
                || command.contains("\r")
                || command.contains("&")
                || command.contains("&&")
                || command.contains("||")
                || command.contains("|")
                || command.contains("`")
                || command.contains("$(")
                || command.contains(">")
                || command.contains("<");
    }
}
