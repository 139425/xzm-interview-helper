package com.xzm.xzm_interview_helper.serveragent;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Runs bounded child processes without transferring any host credential to the browser. */
@Component
public class ServerCommandRunner {
    private final ServerAgentProperties properties;
    private final CredentialRedactor redactor;
    private final Semaphore admission;
    private final ExecutorService outputReaders;

    public ServerCommandRunner(ServerAgentProperties properties, CredentialRedactor redactor) {
        this.properties = properties;
        this.redactor = redactor;
        this.admission = new Semaphore(Math.max(1, properties.getMaxConcurrentCommands()));
        this.outputReaders = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "server-agent-output-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CommandResult runShell(String command, Integer requestedTimeoutSeconds) {
        List<String> invocation = isWindows()
                ? List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                : List.of("/bin/bash", "--noprofile", "--norc", "-c", command);
        return run(invocation, requestedTimeoutSeconds);
    }

    public CommandResult run(List<String> invocation, Integer requestedTimeoutSeconds) {
        if (!admission.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The server command limit is busy");
        }
        long started = System.nanoTime();
        Process process = null;
        try {
            int timeoutSeconds = boundedTimeout(requestedTimeoutSeconds);
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(invocation));
            builder.redirectErrorStream(true);
            redactor.sanitizeEnvironment(builder.environment());
            Path workdir = Path.of(properties.getWorkingDirectory()).toAbsolutePath().normalize();
            if (Files.isDirectory(workdir)) {
                builder.directory(workdir.toFile());
            }
            process = builder.start();
            Process activeProcess = process;
            CompletableFuture<ReadResult> outputFuture = CompletableFuture.supplyAsync(
                    () -> readOutput(activeProcess),
                    outputReaders
            );
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            }
            ReadResult read = outputFuture.get(3, TimeUnit.SECONDS);
            int exitCode = completed ? process.exitValue() : 124;
            String output = redactor.redact(read.output());
            if (!completed) {
                output = output + (output.isBlank() ? "" : System.lineSeparator())
                        + "[command timed out after " + timeoutSeconds + " seconds]";
            }
            return new CommandResult(
                    exitCode,
                    output,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    read.truncated(),
                    !completed
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to run the server operation: " + redactor.redact(exception.getMessage())
            );
        } finally {
            admission.release();
        }
    }

    private ReadResult readOutput(Process process) {
        int limit = Math.max(1_024, properties.getMaxOutputChars());
        StringBuilder retained = new StringBuilder(Math.min(limit, 8_192));
        long seen = 0;
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4_096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                seen += read;
                int remaining = limit - retained.length();
                if (remaining > 0) {
                    retained.append(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException exception) {
            if (retained.isEmpty()) {
                retained.append("[unable to read process output]");
            }
        }
        return new ReadResult(retained.toString(), seen > limit);
    }

    private int boundedTimeout(Integer requested) {
        int configured = Math.max(1, properties.getCommandTimeoutSeconds());
        if (requested == null) return configured;
        return Math.max(1, Math.min(configured, requested));
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @PreDestroy
    public void close() {
        outputReaders.shutdownNow();
    }

    public record CommandResult(
            int exitCode,
            String output,
            long durationMs,
            boolean truncated,
            boolean timedOut
    ) {
    }

    private record ReadResult(String output, boolean truncated) {
    }
}
