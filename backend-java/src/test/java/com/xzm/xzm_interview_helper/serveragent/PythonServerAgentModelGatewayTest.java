package com.xzm.xzm_interview_helper.serveragent;

import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonServerAgentModelGatewayTest {
    @Test
    void collectsTypedContentFromExistingAiTransport() {
        PythonAiGrpcClient client = mock(PythonAiGrpcClient.class);
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setAiProvider("deepseek");
        properties.setAiModel("configured-model");
        when(client.streamChat("user", "system", "professional", "deepseek", "configured-model"))
                .thenReturn(Flux.just(
                        ServerSentEvent.builder("[CONTENT]{\"action\":").build(),
                        ServerSentEvent.builder("[CONTENT]\"FINISH\"}").build(),
                        ServerSentEvent.builder("[DONE]").build()
                ));

        String response = new PythonServerAgentModelGateway(client, properties).decide("system", "user");

        assertThat(response).isEqualTo("{\"action\":\"FINISH\"}");
    }

    @Test
    void incompleteAiStreamFailsClosed() {
        PythonAiGrpcClient client = mock(PythonAiGrpcClient.class);
        ServerAgentProperties properties = new ServerAgentProperties();
        when(client.streamChat("user", "system", "professional", "deepseek", "deepseek-v4-flash"))
                .thenReturn(Flux.just(ServerSentEvent.builder("[CONTENT]partial").build()));

        assertThatThrownBy(() -> new PythonServerAgentModelGateway(client, properties).decide("system", "user"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
