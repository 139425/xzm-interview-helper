import asyncio
import threading
from types import SimpleNamespace

import pytest

import main


@pytest.mark.asyncio
async def test_rag_indexing_does_not_block_grpc_or_app_readiness(monkeypatch):
    grpc_started = asyncio.Event()
    stop_grpc = asyncio.Event()
    index_started = threading.Event()
    release_index = threading.Event()

    settings = SimpleNamespace(
        host="127.0.0.1",
        port=9090,
        grpc_host="127.0.0.1",
        grpc_port=50051,
        model_name="test-model",
        bigmodel_base_url="https://example.invalid/v1",
    )

    class BlockingRagService:
        def index_docs_directory(self):
            index_started.set()
            release_index.wait(timeout=2)

    async def fake_grpc_server(host, port):
        assert (host, port) == ("127.0.0.1", 50051)
        grpc_started.set()
        await stop_grpc.wait()

    monkeypatch.setattr(main, "validate_config", lambda: None)
    monkeypatch.setattr(main, "get_settings", lambda: settings)
    monkeypatch.setattr(main, "get_rag_service", lambda: BlockingRagService())
    monkeypatch.setattr(main, "start_grpc_server", fake_grpc_server)

    try:
        async with main.lifespan(None):
            await asyncio.wait_for(grpc_started.wait(), timeout=0.5)
            assert await asyncio.to_thread(index_started.wait, 0.5)
    finally:
        release_index.set()
        stop_grpc.set()
        main.grpc_server_task = None
        main.rag_index_task = None
