package com.xzm.xzm_interview_helper.grpc.client;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC客户端服务 - 调用Python AI后端
 *
 * 通过gRPC协议调用Python后端的流式接口和面试模块接口
 */
@Service
@Slf4j
public class PythonAiGrpcClient {

    @GrpcClient("python-ai-backend")
    private PythonAiChatServiceGrpc.PythonAiChatServiceStub asyncStub;

    @GrpcClient("python-ai-backend")
    private PythonAiChatServiceGrpc.PythonAiChatServiceBlockingStub blockingStub;

    @GrpcClient("python-ai-backend")
    private PythonAiChatServiceGrpc.PythonAiChatServiceFutureStub futureStub;

    /**
     * 调用Python后端的思考模式流式对话接口
     * 
     * @param message 用户消息
     * @return 流式响应
     */
    public Flux<ServerSentEvent<String>> streamThinkChat(String message) {
        return streamThinkChat(message, null);
    }

    /**
     * 调用Python后端的思考模式流式对话接口（带系统提示词）
     * 
     * @param message 用户消息
     * @param systemPrompt 系统提示词（可选）
     * @return 流式响应
     */
    public Flux<ServerSentEvent<String>> streamThinkChat(String message, String systemPrompt) {
        return streamThinkChat(message, systemPrompt, "professional");
    }

    public Flux<ServerSentEvent<String>> streamThinkChat(String message, String systemPrompt, String promptMode) {
        return streamThinkChat(message, systemPrompt, promptMode, "zhipu", null);
    }

    public Flux<ServerSentEvent<String>> streamThinkChat(
            String message,
            String systemPrompt,
            String promptMode,
            String provider,
            String modelName
    ) {
        return streamChatWithOptions(message, systemPrompt, promptMode, provider, modelName, true);
    }

    /**
     * 调用Python后端的非思考模式流式对话接口（带系统提示词）
     *
     * @param message 用户消息
     * @param systemPrompt 系统提示词（可选）
     * @return 流式响应
     */
    public Flux<ServerSentEvent<String>> streamChat(String message, String systemPrompt) {
        return streamChat(message, systemPrompt, "professional");
    }

    public Flux<ServerSentEvent<String>> streamChat(String message, String systemPrompt, String promptMode) {
        return streamChat(message, systemPrompt, promptMode, "zhipu", null);
    }

    public Flux<ServerSentEvent<String>> streamChat(
            String message,
            String systemPrompt,
            String promptMode,
            String provider,
            String modelName
    ) {
        return streamChatWithOptions(message, systemPrompt, promptMode, provider, modelName, false);
    }

    private Flux<ServerSentEvent<String>> streamChatWithOptions(
            String message,
            String systemPrompt,
            String promptMode,
            String provider,
            String modelName,
            boolean enableThinking
    ) {
        log.info("gRPC Client starting chat stream: messageLength={}",
                message == null ? 0 : message.length());

        return Flux.create(sink -> {
            AtomicReference<ClientCallStreamObserver<ThinkChatRequest>> grpcCall = new AtomicReference<>();
            AtomicBoolean terminal = new AtomicBoolean(false);
            AtomicBoolean clientCancelled = new AtomicBoolean(false);
            sink.onCancel(() -> {
                clientCancelled.set(true);
                ClientCallStreamObserver<ThinkChatRequest> call = grpcCall.get();
                if (call != null) {
                    call.cancel("HTTP client cancelled chat stream", null);
                }
            });
            try {
                // 构建请求
                ThinkChatRequest.Builder requestBuilder = ThinkChatRequest.newBuilder()
                        .setMessage(message);
                
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    requestBuilder.setSystemPrompt(systemPrompt);
                }

                if (promptMode != null && !promptMode.isEmpty()) {
                    requestBuilder.setPromptMode(promptMode);
                }
                if (modelName != null && !modelName.isEmpty()) {
                    requestBuilder.setModelName(modelName);
                }
                if (provider != null && !provider.isEmpty()) {
                    requestBuilder.setProvider(provider);
                }
                requestBuilder.setEnableThinking(enableThinking);
                
                ThinkChatRequest request = requestBuilder.build();

                // 发起gRPC流式调用
                asyncStub.withDeadlineAfter(120, TimeUnit.SECONDS).streamThinkChat(
                        request,
                        new ClientResponseObserver<ThinkChatRequest, ThinkChatResponse>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<ThinkChatRequest> requestStream) {
                        grpcCall.set(requestStream);
                        if (clientCancelled.get() || sink.isCancelled()) {
                            requestStream.cancel("HTTP client cancelled chat stream", null);
                        }
                    }

                    @Override
                    public void onNext(ThinkChatResponse response) {
                        if (terminal.get() || clientCancelled.get() || sink.isCancelled()) {
                            return;
                        }
                        PythonResponseType type = response.getType();
                        String decodedContent = response.getContent();
                        String formattedContent = formatChatFrame(type, decodedContent, enableThinking);
                        
                        log.debug("gRPC Client received chat response: type={}, contentLength={}",
                                type, decodedContent.length());

                        if (formattedContent != null && !formattedContent.isEmpty()) {
                            sink.next(ServerSentEvent.<String>builder()
                                    .data(formattedContent)
                                    .build());
                        }
                        if (isTerminalChatResponse(type) && terminal.compareAndSet(false, true)) {
                            ClientCallStreamObserver<ThinkChatRequest> call = grpcCall.get();
                            if (call != null) {
                                call.cancel(
                                        type == PythonResponseType.PY_DONE
                                                ? "Python chat service completed with a DONE frame"
                                                : "Python chat service returned an error frame",
                                        null
                                );
                            }
                            if (!sink.isCancelled()) {
                                sink.complete();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("gRPC Client 调用出错: {}", t.getMessage(), t);
                        if (terminal.compareAndSet(false, true) && !clientCancelled.get() && !sink.isCancelled()) {
                            sink.next(ServerSentEvent.<String>builder()
                                    .data("[ERROR]服务暂时不可用，请稍后重试。")
                                    .build());
                            sink.complete();
                        }
                    }

                    @Override
                    public void onCompleted() {
                        log.info("gRPC Client 调用完成");
                        if (terminal.compareAndSet(false, true) && !clientCancelled.get() && !sink.isCancelled()) {
                            sink.complete();
                        }
                    }
                });
                
            } catch (Exception e) {
                log.error("gRPC Client 调用异常: {}", e.getMessage(), e);
                if (terminal.compareAndSet(false, true) && !clientCancelled.get() && !sink.isCancelled()) {
                    sink.next(ServerSentEvent.<String>builder()
                            .data("[ERROR]服务暂时不可用，请稍后重试。")
                            .build());
                    sink.complete();
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 调用Python后端生成面试问题（阻塞调用）
     *
     * @param resumeText 简历文本
     * @return 生成的问题列表
     */
    public GenerateQuestionsResponse generateQuestions(String resumeText) {
        log.info("gRPC Client: 调用Python后端 GenerateQuestions, resumeText长度={}", resumeText.length());
        try {
            GenerateQuestionsRequest request = GenerateQuestionsRequest.newBuilder()
                    .setResumeText(resumeText)
                    .build();
            GenerateQuestionsResponse response = blockingStub.generateQuestions(request);
            log.info("gRPC Client: GenerateQuestions 返回 {} 个问题, success={}",
                    response.getQuestionsList().size(), response.getSuccess());
            return response;
        } catch (Exception e) {
            log.error("gRPC Client: GenerateQuestions 调用异常: {}", e.getMessage(), e);
            return GenerateQuestionsResponse.newBuilder()
                    .setSuccess(false)
                    .setError("gRPC调用失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 调用Python后端评价用户回答（阻塞调用）
     *
     * @param question 面试问题
     * @param answer 用户回答
     * @return 评价结果
     */
    public EvaluateAnswerResponse evaluateAnswer(String question, String answer) {
        log.info("gRPC Client: 调用Python后端 EvaluateAnswer");
        try {
            EvaluateAnswerRequest request = EvaluateAnswerRequest.newBuilder()
                    .setQuestion(question != null ? question : "")
                    .setAnswer(answer != null ? answer : "")
                    .build();
            EvaluateAnswerResponse response = blockingStub.evaluateAnswer(request);
            log.info("gRPC Client: EvaluateAnswer 返回 score={}, success={}",
                    response.getScore(), response.getSuccess());
            return response;
        } catch (Exception e) {
            log.error("gRPC Client: EvaluateAnswer 调用异常: {}", e.getMessage(), e);
            return EvaluateAnswerResponse.newBuilder()
                    .setSuccess(false)
                    .setError("gRPC调用失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 调用Python后端生成面试总结（阻塞调用）
     *
     * @param interviewRecord 面试记录文本
     * @return 面试总结
     */
    public GenerateSummaryResponse generateSummary(String interviewRecord) {
        log.info("gRPC Client: 调用Python后端 GenerateSummary, record长度={}", interviewRecord.length());
        try {
            GenerateSummaryRequest request = GenerateSummaryRequest.newBuilder()
                    .setInterviewRecord(interviewRecord)
                    .build();
            GenerateSummaryResponse response = blockingStub.generateSummary(request);
            log.info("gRPC Client: GenerateSummary 返回 success={}", response.getSuccess());
            return response;
        } catch (Exception e) {
            log.error("gRPC Client: GenerateSummary 调用异常: {}", e.getMessage(), e);
            return GenerateSummaryResponse.newBuilder()
                    .setSuccess(false)
                    .setError("gRPC调用失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Converts a typed Python response into the public SSE protocol.
     *
     * <p>The protobuf type is authoritative: model-controlled text can never become a terminal
     * or stage frame merely by starting with a reserved marker.</p>
     */
    static String formatChatFrame(
            PythonResponseType type,
            String decodedContent,
            boolean enableThinking
    ) {
        return switch (type) {
            case PY_STAGE -> "[STAGE]" + decodedContent;
            case PY_THINKING -> enableThinking
                    ? "[THINKING]" + decodedContent
                    : null;
            case PY_CONTENT -> "[CONTENT]" + decodedContent;
            case PY_DONE -> "[DONE]";
            case PY_ERROR -> "[ERROR]服务暂时不可用，请稍后重试。";
            // Unknown/future response types are protocol mismatches.  Fail closed instead of
            // silently dropping a control frame and later accepting DONE as a valid answer.
            default -> "[ERROR]服务暂时不可用，请稍后重试。";
        };
    }

    static boolean isTerminalChatResponse(PythonResponseType type) {
        // Only the three explicitly non-terminal payload types may keep the stream open.  This
        // deliberately makes unknown future enum values terminal until the gateway is upgraded.
        return type != PythonResponseType.PY_THINKING
                && type != PythonResponseType.PY_CONTENT
                && type != PythonResponseType.PY_STAGE;
    }

    /**
     * Runs one bounded, structured interview-agent decision. Unlike the old fixed interview RPCs,
     * this request carries the server-owned dialogue state and model capability options.
     */
    public InterviewAgentResponse runInterviewAgent(InterviewAgentRequest request) {
        return runInterviewAgent(request, new AtomicBoolean(false));
    }

    /**
     * Runs a unary model request while observing the HTTP/SSE cancellation signal. Cancelling the
     * future propagates a gRPC cancellation to Python instead of letting an abandoned interview
     * keep consuming a model call until the deadline expires.
     */
    public InterviewAgentResponse runInterviewAgent(InterviewAgentRequest request, AtomicBoolean cancelled) {
        ListenableFuture<InterviewAgentResponse> future = futureStub
                .withDeadlineAfter(120, TimeUnit.SECONDS)
                .runInterviewAgent(request);
        try {
            while (true) {
                if (cancelled != null && cancelled.get()) {
                    future.cancel(true);
                    throw new CancellationException("Interview request cancelled by client");
                }
                try {
                    return future.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    // Poll the cancellation signal without extending the gRPC deadline.
                }
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new CancellationException("Interview request interrupted");
        } catch (ExecutionException exception) {
            log.error("gRPC Client: RunInterviewAgent failed", exception.getCause());
            return InterviewAgentResponse.newBuilder()
                    .setSuccess(false)
                    .setError("AI service is temporarily unavailable")
                    .build();
        } catch (Exception exception) {
            log.error("gRPC Client: RunInterviewAgent failed: {}", exception.getMessage(), exception);
            return InterviewAgentResponse.newBuilder()
                    .setSuccess(false)
                    .setError("AI service is temporarily unavailable")
                    .build();
        }
    }
}
