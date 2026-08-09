"""
Python AI 后端服务入口。
提供智谱 GLM 思考模式流式对话接口，并启动 gRPC 服务。
"""

import logging
import asyncio
import os
from fastapi import FastAPI
from contextlib import asynccontextmanager
import uvicorn

from config import get_settings, validate_config
from routers import chat_router
from services.rag_service import get_rag_service

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# gRPC 任务引用
grpc_server_task = None
rag_index_task = None


async def start_grpc_server(host: str, port: int):
    """后台启动 gRPC 服务。"""
    try:
        from grpc_server import serve_grpc
        await serve_grpc(host=host, port=port)
    except ImportError as e:
        logger.warning(f"gRPC server not available (run generate_proto.py first): {e}")
    except Exception as e:
        logger.error(f"Failed to start gRPC server: {e}")


async def initialize_rag_index():
    """Build retrieval indexes without blocking HTTP or gRPC startup."""
    try:
        rag_service = get_rag_service()
        await asyncio.to_thread(rag_service.index_docs_directory)
        logger.info("RAG document indexing completed")
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning(
            "RAG initialization failed; serving without document context: %s",
            e,
        )


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    应用生命周期处理。
    启动时校验配置并启动 gRPC 服务。
    """
    global grpc_server_task, rag_index_task
    
    # 启动时校验配置
    try:
        validate_config()
        settings = get_settings()
        logger.info("[OK] Configuration loaded successfully")
        logger.info("[OK] HTTP Server will run on %s:%s", settings.host, settings.port)
        logger.info(
            "[OK] gRPC Server will run on %s:%s",
            settings.grpc_host,
            settings.grpc_port,
        )
        logger.info("[OK] Using model: %s", settings.model_name)
        logger.info(
            "[OK] API URL: %s/chat/completions",
            settings.bigmodel_base_url.rstrip("/"),
        )
        print("[OK] Configuration loaded successfully")
        print(f"[OK] HTTP Server will run on {settings.host}:{settings.port}")
        print(f"[OK] gRPC Server will run on {settings.grpc_host}:{settings.grpc_port}")
        print(f"[OK] Using model: {settings.model_name}")
        
        # 后台启动 gRPC 服务
        grpc_server_task = asyncio.create_task(
            start_grpc_server(settings.grpc_host, settings.grpc_port)
        )

        # Indexing may call external embedding providers and scan local files.
        # Keep it off the event loop so HTTP/gRPC readiness is independent of
        # retrieval-provider health.
        rag_index_task = asyncio.create_task(initialize_rag_index())
        
    except ValueError as e:
        logger.error("[ERROR] Configuration error: %s", e)
        print(f"[ERROR] Configuration error: {e}")
        raise
    
    yield
    
    # 关闭时清理
    if grpc_server_task:
        grpc_server_task.cancel()
        try:
            await grpc_server_task
        except asyncio.CancelledError:
            pass

    if rag_index_task and not rag_index_task.done():
        rag_index_task.cancel()
        try:
            await rag_index_task
        except asyncio.CancelledError:
            pass
    
    logger.info("Shutting down Python AI Backend Service...")
    print("Shutting down Python AI Backend Service...")


# 创建 FastAPI 应用
app = FastAPI(
    title="Python AI Backend Service",
    description="智谱 GLM 思考模式流式对话 API",
    version="1.0.0",
    lifespan=lifespan
)


# 注册路由
app.include_router(chat_router.router)


if __name__ == "__main__":
    settings = get_settings()
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        reload=os.getenv("UVICORN_RELOAD", "false").strip().lower() == "true",
    )
