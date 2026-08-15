package com.xzm.xzm_interview_helper.serveragent;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/** Restricts file tools to configured operational roots and excludes credential stores. */
@Component
public class ServerPathPolicy {
    private final List<Path> allowedRoots;

    public ServerPathPolicy(ServerAgentProperties properties) {
        this.allowedRoots = properties.getAllowedRoots().stream()
                .filter(root -> root != null && !root.isBlank())
                .map(root -> Path.of(root).toAbsolutePath().normalize())
                .toList();
    }

    public Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file path is required");
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        Path configuredRoot = allowedRoots.stream().filter(path::startsWith).findFirst().orElse(null);
        if (configuredRoot == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Path is outside the configured server roots");
        }
        rejectCredentialPath(path);
        return canonicalizeInsideRoot(path, configuredRoot);
    }

    private void rejectCredentialPath(Path path) {
        String lower = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (lower.contains("/.ssh/")
                || lower.endsWith("/.ssh")
                || lower.contains("/.gnupg/")
                || lower.contains("/.aws/")
                || lower.contains("/.kube/")
                || lower.endsWith("/.env")
                || lower.contains("/.env.")
                || lower.contains("credential")
                || lower.contains("secret")
                || lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.endsWith(".p12")
                || lower.endsWith(".pfx")
                || lower.endsWith(".jks")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credential files are not available to the web agent");
        }
    }

    private Path canonicalizeInsideRoot(Path path, Path configuredRoot) {
        try {
            Path boundary = Files.exists(configuredRoot) ? configuredRoot.toRealPath() : configuredRoot;
            Path existing = path;
            while (existing != null && !Files.exists(existing)) existing = existing.getParent();
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unable to verify the server path");
            }
            Path existingReal = existing.toRealPath();
            Path remainder = existing.relativize(path);
            Path canonical = existingReal.resolve(remainder).normalize();
            if (!canonical.startsWith(boundary)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Path resolves outside the configured server roots");
            }
            // A harmless-looking in-root symlink may point at .env/private.pem inside the
            // same root. Re-run the credential policy against the resolved target.
            rejectCredentialPath(canonical);
            return canonical;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unable to verify the server path");
        }
    }
}
