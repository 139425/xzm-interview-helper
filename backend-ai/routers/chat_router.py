"""
聊天路由模块。
提供智谱 GLM 思考模式的 SSE 流式接口。
"""

from fastapi import APIRouter, Query, HTTPException
from sse_starlette.sse import EventSourceResponse
from typing import AsyncGenerator

from services.zhipu_service import get_zhipu_service


router = APIRouter(prefix="/api/chat", tags=["chat"])


async def event_generator(message: str) -> AsyncGenerator[dict, None]:
    """
    从智谱服务生成 SSE 事件。
    """
    service = get_zhipu_service()
    
    async for chunk in service.stream_think_chat(message):
        yield {"data": chunk}


@router.get("/stream-think")
async def stream_think_chat(
    message: str = Query(
        ..., 
        min_length=1,
        description="发送给 AI 的用户消息"
    )
) -> EventSourceResponse:
    """
    思考模式 SSE 流式接口。
    """
    if not message or not message.strip():
        raise HTTPException(
            status_code=400, 
            detail="消息不能为空"
        )
    
    return EventSourceResponse(
        event_generator(message),
        media_type="text/event-stream"
    )
