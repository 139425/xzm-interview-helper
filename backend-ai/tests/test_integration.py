"""
Python AI 后端集成测试。

注意：这些测试需要服务已启动，并且可能调用真实模型提供商。
仅在显式设置 RUN_LIVE_INTEGRATION_TESTS=true 时运行，避免普通 pytest
把“外部进程未启动”误报为代码回归。
"""

import os

import pytest
import httpx

# 测试配置
RUN_LIVE_INTEGRATION_TESTS = (
    os.getenv("RUN_LIVE_INTEGRATION_TESTS", "").strip().lower() == "true"
)
BASE_URL = os.getenv("AI_BACKEND_TEST_BASE_URL", "http://127.0.0.1:9090").rstrip("/")
TIMEOUT = 30.0

pytestmark = pytest.mark.skipif(
    not RUN_LIVE_INTEGRATION_TESTS,
    reason="set RUN_LIVE_INTEGRATION_TESTS=true to test a running AI backend",
)


class TestStreamThinkEndpoint:
    """思考模式流式接口测试。"""

    @pytest.mark.asyncio
    async def test_stream_think_requires_message(self):
        """缺少 message 参数应返回 422。"""
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{BASE_URL}/api/chat/stream-think",
                timeout=TIMEOUT
            )
            assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_stream_think_rejects_empty_message(self):
        """空 message 应返回 422。"""
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{BASE_URL}/api/chat/stream-think?message=",
                timeout=TIMEOUT
            )
            assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_stream_think_returns_sse_content_type(self):
        """接口应返回 SSE content-type。"""
        async with httpx.AsyncClient() as client:
            async with client.stream(
                "GET",
                f"{BASE_URL}/api/chat/stream-think?message=hello",
                timeout=TIMEOUT
            ) as response:
                assert response.status_code == 200
                content_type = response.headers.get("content-type", "")
                assert "text/event-stream" in content_type
