"""
Python AI 后端 gRPC 服务。
提供智谱 GLM 思考模式流式接口 + 面试模块非流式接口。
"""

import asyncio
import logging
from concurrent import futures
import grpc
from grpc import aio

from services.interview_agent_service import (
    ACTION_END_INTERVIEW,
    InterviewAgentRequestData,
    get_interview_agent_service,
)
from services.zhipu_service import get_zhipu_service

# 导入生成的 protobuf 类
import longcat_chat_pb2
import longcat_chat_pb2_grpc

logger = logging.getLogger(__name__)


class PythonAiChatServicer(longcat_chat_pb2_grpc.PythonAiChatServiceServicer):
    """Python AI 聊天的 gRPC 服务实现。"""

    def __init__(self):
        self.zhipu_service = get_zhipu_service()
        self.interview_agent_service = get_interview_agent_service()

    async def StreamThinkChat(self, request, context):
        """思考模式流式对话实现。"""
        message = request.message
        system_prompt = request.system_prompt if request.HasField('system_prompt') else None
        model_name = request.model_name if request.HasField('model_name') else None
        enable_thinking = request.enable_thinking if request.HasField('enable_thinking') else None
        prompt_mode = request.prompt_mode if request.HasField('prompt_mode') else None
        provider = request.provider if request.HasField('provider') else None

        logger.info(
            "gRPC StreamThinkChat: message=%s... prompt_mode=%s provider=%s model=%s thinking=%s",
            message[:50],
            prompt_mode,
            provider,
            model_name,
            enable_thinking,
        )

        try:
            async for chunk in self.zhipu_service.stream_think_chat(
                message,
                system_prompt,
                model_name=model_name,
                enable_thinking=enable_thinking,
                prompt_mode=prompt_mode,
                provider=provider,
            ):
                response = longcat_chat_pb2.ThinkChatResponse()

                if chunk.startswith("[STAGE]"):
                    response.type = longcat_chat_pb2.PY_STAGE
                    response.content = chunk[len("[STAGE]"):]
                elif chunk.startswith("[THINKING]"):
                    response.type = longcat_chat_pb2.PY_THINKING
                    response.content = chunk[len("[THINKING]"):]
                elif chunk.startswith("[CONTENT]"):
                    response.type = longcat_chat_pb2.PY_CONTENT
                    response.content = chunk[len("[CONTENT]"):]
                elif chunk == "[DONE]":
                    response.type = longcat_chat_pb2.PY_DONE
                    response.content = ""
                elif chunk.startswith("[ERROR]"):
                    response.type = longcat_chat_pb2.PY_ERROR
                    response.content = chunk[len("[ERROR]"):]
                else:
                    logger.error("gRPC stream received an untyped protocol frame")
                    response.type = longcat_chat_pb2.PY_ERROR
                    response.content = "The AI stream returned an invalid frame."
                    yield response
                    return

                yield response

        except Exception:
            logger.exception("gRPC StreamThinkChat unexpected error")
            error_response = longcat_chat_pb2.ThinkChatResponse()
            error_response.type = longcat_chat_pb2.PY_ERROR
            # Provider/configuration diagnostics stay in server logs.  The public typed error
            # frame carries a stable candidate-safe message.
            error_response.content = "The AI service is temporarily unavailable."
            yield error_response

    async def GenerateQuestions(self, request, context):
        """生成面试问题（非流式）。"""
        logger.info(f"gRPC GenerateQuestions: resume_text length={len(request.resume_text)}")
        try:
            questions = await self.zhipu_service.generate_questions(request.resume_text)
            return longcat_chat_pb2.GenerateQuestionsResponse(
                questions=questions,
                success=True,
            )
        except Exception as e:
            logger.error(f"gRPC GenerateQuestions error: {e}", exc_info=True)
            return longcat_chat_pb2.GenerateQuestionsResponse(
                success=False,
                error=str(e),
            )

    async def EvaluateAnswer(self, request, context):
        """评价用户回答（非流式）。"""
        logger.info(f"gRPC EvaluateAnswer: question={request.question[:50]}...")
        try:
            result = await self.zhipu_service.evaluate_answer(request.question, request.answer)
            return longcat_chat_pb2.EvaluateAnswerResponse(
                knowledge_tags=result["knowledge_tags"],
                score=result["score"],
                evaluation=result["evaluation"],
                reference_answer=result["reference_answer"],
                success=True,
            )
        except Exception as e:
            logger.error(f"gRPC EvaluateAnswer error: {e}", exc_info=True)
            return longcat_chat_pb2.EvaluateAnswerResponse(
                success=False,
                error=str(e),
            )

    async def GenerateSummary(self, request, context):
        """生成面试总结（非流式）。"""
        logger.info(f"gRPC GenerateSummary: record length={len(request.interview_record)}")
        try:
            summary = await self.zhipu_service.generate_summary(request.interview_record)
            return longcat_chat_pb2.GenerateSummaryResponse(
                summary=summary,
                success=True,
            )
        except Exception as e:
            logger.error(f"gRPC GenerateSummary error: {e}", exc_info=True)
            return longcat_chat_pb2.GenerateSummaryResponse(
                success=False,
                error=str(e),
            )

    async def RunInterviewAgent(self, request, context):
        """Run one persisted-state interview-agent decision.

        The request may contain a resume and candidate answer, so logging is
        intentionally limited to operation and counters rather than raw text.
        """
        logger.info(
            "gRPC RunInterviewAgent: operation=%s total=%s primary=%s follow_up=%s",
            request.operation,
            request.total_question_count,
            request.primary_question_count,
            request.follow_up_count,
        )
        try:
            result = await self.interview_agent_service.run(
                InterviewAgentRequestData.from_grpc(request)
            )
            return longcat_chat_pb2.InterviewAgentResponse(
                **result.to_grpc_kwargs(),
            )
        except Exception:
            logger.exception("gRPC RunInterviewAgent unexpected error")
            return longcat_chat_pb2.InterviewAgentResponse(
                action=ACTION_END_INTERVIEW,
                decision_note="The interview request could not be processed safely.",
                success=False,
                error="The interview agent is temporarily unavailable.",
            )


def format_grpc_listen_address(host: str, port: int) -> str:
    """Format IPv4/IPv6 gRPC bind addresses without widening exposure."""
    normalized_host = (host or "127.0.0.1").strip()
    if ":" in normalized_host and not normalized_host.startswith("["):
        return f"[{normalized_host}]:{port}"
    return f"{normalized_host}:{port}"


async def serve_grpc(host: str = "127.0.0.1", port: int = 50051):
    """
    启动 gRPC 服务。
    """
    server = aio.server(futures.ThreadPoolExecutor(max_workers=10))
    longcat_chat_pb2_grpc.add_PythonAiChatServiceServicer_to_server(
        PythonAiChatServicer(), server
    )
    
    listen_addr = format_grpc_listen_address(host, port)
    server.add_insecure_port(listen_addr)
    
    logger.info(f"Starting gRPC server on port {port}")
    await server.start()
    logger.info(f"gRPC server started on {listen_addr}")
    
    try:
        await server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Shutting down gRPC server...")
        await server.stop(5)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    from config import get_settings

    settings = get_settings()
    asyncio.run(serve_grpc(host=settings.grpc_host, port=settings.grpc_port))
