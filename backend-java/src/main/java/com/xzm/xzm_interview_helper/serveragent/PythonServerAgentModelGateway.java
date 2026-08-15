package com.xzm.xzm_interview_helper.serveragent;

import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** Uses the existing Java-to-Python AI transport; API keys remain in the Python service. */
@Component
public class PythonServerAgentModelGateway implements ServerAgentModelGateway {
    private final PythonAiGrpcClient pythonAiGrpcClient;
    private final ServerAgentProperties properties;

    public PythonServerAgentModelGateway(
            PythonAiGrpcClient pythonAiGrpcClient,
            ServerAgentProperties properties
    ) {
        this.pythonAiGrpcClient = pythonAiGrpcClient;
        this.properties = properties;
    }

    @Override
    public String decide(String systemPrompt, String userPrompt) {
        List<String> frames = pythonAiGrpcClient.streamChat(
                        userPrompt,
                        systemPrompt,
                        "server_agent",
                        properties.getAiProvider(),
                        properties.getAiModel()
                )
                .map(ServerSentEvent::data)
                .collectList()
                .block(Duration.ofSeconds(Math.max(5, properties.getAiTimeoutSeconds())));
        if (frames == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server agent returned no response");
        }
        StringBuilder content = new StringBuilder();
        boolean done = false;
        for (String frame : frames) {
            if (frame == null) continue;
            if (frame.startsWith("[CONTENT]")) {
                content.append(frame.substring("[CONTENT]".length()));
            } else if (frame.startsWith("[ERROR]")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server agent is temporarily unavailable");
            } else if (frame.equals("[DONE]")) {
                done = true;
            }
        }
        if (!done || content.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server agent did not complete a decision");
        }
        return content.toString();
    }
}
