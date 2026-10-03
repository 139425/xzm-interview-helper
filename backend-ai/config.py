"""
Python AI 后端服务配置模块。
从环境变量读取配置并提供默认值。
"""

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import BaseModel, Field, AliasChoices


class DeepSeekLocalConfig(BaseModel):
    api_key: str
    base_url: str = "https://api.deepseek.com"


class Settings(BaseSettings):
    """
    应用配置，来自环境变量。

    必填：
        - bigmodel_api_key：智谱 API Key

    可选（有默认值）：
        - bigmodel_base_url：智谱 OpenAI 兼容接口地址
        - port：HTTP 服务端口
        - model_name：模型名称
        - max_tokens：最大输出 tokens
        - temperature：采样温度
    """

    # 必填配置
    bigmodel_api_key: str = Field(
        ...,
        description="智谱 API Key",
        validation_alias=AliasChoices(
            "BIGMODEL_API_KEY",
            "ZHIPU_API_KEY",
            "ZAI_API_KEY",
            "LONGCAT_API_KEY",
        ),
    )

    # 可选配置
    bigmodel_base_url: str = Field(
        default="https://open.bigmodel.cn/api/paas/v4/",
        description="智谱 OpenAI 兼容接口地址",
        validation_alias=AliasChoices(
            "BIGMODEL_BASE_URL",
            "ZHIPU_BASE_URL",
            "ZAI_BASE_URL",
            "LONGCAT_BASE_URL",
        ),
    )

    port: int = Field(
        default=9090,
        description="HTTP 服务端口"
    )

    # Python AI is designed to be reached by the co-located Java service.
    # Keep both listeners loopback-only unless an operator explicitly changes
    # these values for a protected deployment topology.
    host: str = Field(
        default="127.0.0.1",
        validation_alias=AliasChoices("HOST", "AI_HOST", "PYTHON_AI_HOST"),
    )

    grpc_host: str = Field(
        default="127.0.0.1",
        validation_alias=AliasChoices("GRPC_HOST", "AI_GRPC_HOST", "PYTHON_AI_GRPC_HOST"),
    )

    grpc_port: int = Field(
        default=50051,
        validation_alias=AliasChoices("GRPC_PORT", "AI_GRPC_PORT", "PYTHON_AI_GRPC_PORT"),
    )

    model_name: str = Field(
        default="GLM-4.7-Flash",
        description="模型名称",
        validation_alias=AliasChoices(
            "MODEL_NAME",
            "BIGMODEL_MODEL_NAME",
            "ZHIPU_MODEL_NAME",
            "LONGCAT_MODEL_NAME",
        )
    )

    # Interview-agent defaults. Individual RunInterviewAgent calls can
    # override them so a model migration does not require a workflow rewrite.
    interview_agent_provider: str = Field(
        default="deepseek",
        validation_alias=AliasChoices(
            "INTERVIEW_AGENT_PROVIDER",
            "AI_INTERVIEW_PROVIDER",
        ),
    )

    interview_agent_model_name: str = Field(
        default="deepseek-v4-pro",
        validation_alias=AliasChoices(
            "INTERVIEW_AGENT_MODEL_NAME",
            "AI_INTERVIEW_MODEL_NAME",
        ),
    )

    interview_agent_enable_thinking: bool = Field(
        default=False,
        validation_alias=AliasChoices(
            "INTERVIEW_AGENT_ENABLE_THINKING",
            "AI_INTERVIEW_ENABLE_THINKING",
        ),
    )

    # Request-level model selection is deliberately opt-in. Defaults remain
    # server policy so a session field cannot spend quota on an arbitrary model.
    interview_agent_allow_request_model_override: bool = Field(
        default=True,
        validation_alias=AliasChoices("INTERVIEW_AGENT_ALLOW_REQUEST_MODEL_OVERRIDE"),
    )

    interview_agent_allowed_providers: str = Field(
        default="deepseek",
        validation_alias=AliasChoices("INTERVIEW_AGENT_ALLOWED_PROVIDERS"),
    )

    interview_agent_allowed_models: str = Field(
        default="deepseek-v4-flash,deepseek-v4-pro",
        validation_alias=AliasChoices("INTERVIEW_AGENT_ALLOWED_MODELS"),
    )

    interview_agent_allow_request_thinking_override: bool = Field(
        default=True,
        validation_alias=AliasChoices("INTERVIEW_AGENT_ALLOW_REQUEST_THINKING_OVERRIDE"),
    )

    max_tokens: int = Field(
        default=8000,
        description="最大输出 tokens"
    )

    temperature: float = Field(
        default=0.7,
        description="采样温度"
    )

    # RAG 配置
    siliconflow_api_key: str = Field(
        default="",
        description="SiliconFlow API Key"
    )

    siliconflow_base_url: str = Field(
        default="https://api.siliconflow.cn/v1",
        description="SiliconFlow API 地址"
    )

    embedding_model: str = Field(
        default="BAAI/bge-large-zh-v1.5",
        description="嵌入模型名称"
    )

    embed_max_tokens: int = Field(
        default=480,
        description="单条嵌入文本的安全 token 上限"
    )

    embedding_dimensions: Optional[int] = Field(
        default=None,
        validation_alias=AliasChoices("EMBEDDING_DIMENSIONS"),
        description="可选输出维度；只为供应商明确支持 dimensions 的模型设置",
    )

    embed_timeout_seconds: float = Field(
        default=20.0,
        validation_alias=AliasChoices("EMBED_TIMEOUT_SECONDS"),
    )

    embed_max_retries: int = Field(
        default=2,
        validation_alias=AliasChoices("EMBED_MAX_RETRIES"),
    )

    docs_dir: str = Field(
        default="docs",
        description="文档目录路径"
    )

    chroma_persist_dir: str = Field(
        default="chroma_db",
        description="ChromaDB 持久化目录"
    )

    chunk_size: int = Field(
        default=500,
        description="文档分块大小"
    )

    chunk_overlap: int = Field(
        default=100,
        description="文档分块重叠"
    )

    rag_top_k: int = Field(
        default=5,
        description="RAG 检索返回数量"
    )

    rag_collection_name: str = Field(
        default="docs_v2",
        validation_alias=AliasChoices("RAG_COLLECTION_NAME"),
        description="版本化 RAG collection；设置为 docs 可立即回滚旧索引",
    )

    rag_dense_candidates: int = Field(
        default=30,
        validation_alias=AliasChoices("RAG_DENSE_CANDIDATES"),
    )

    rag_lexical_candidates: int = Field(
        default=30,
        validation_alias=AliasChoices("RAG_LEXICAL_CANDIDATES"),
    )

    rag_fusion_candidates: int = Field(
        default=40,
        validation_alias=AliasChoices("RAG_FUSION_CANDIDATES"),
    )

    rag_min_rerank_score: float = Field(
        default=0.08,
        validation_alias=AliasChoices("RAG_MIN_RERANK_SCORE"),
    )

    rag_min_query_coverage: float = Field(
        default=0.25,
        validation_alias=AliasChoices("RAG_MIN_QUERY_COVERAGE"),
    )

    rag_max_chunks_per_parent: int = Field(
        default=2,
        validation_alias=AliasChoices("RAG_MAX_CHUNKS_PER_PARENT"),
    )

    rag_chunk_target_tokens: int = Field(
        default=280,
        validation_alias=AliasChoices("RAG_CHUNK_TARGET_TOKENS"),
    )

    rag_chunk_max_tokens: int = Field(
        default=360,
        validation_alias=AliasChoices("RAG_CHUNK_MAX_TOKENS"),
    )

    rag_chunk_overlap_tokens: int = Field(
        default=60,
        validation_alias=AliasChoices("RAG_CHUNK_OVERLAP_TOKENS"),
    )

    embed_batch_size: int = Field(
        default=64,
        description="嵌入请求的批量大小"
    )

    embed_max_chars_per_batch: int = Field(
        default=200000,
        description="单次嵌入请求最大字符数"
    )

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache()
def get_settings() -> Settings:
    """
    获取缓存的配置实例。
    """
    return Settings()


@lru_cache()
def get_deepseek_config() -> DeepSeekLocalConfig:
    env_api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
    if env_api_key:
        return DeepSeekLocalConfig(
            api_key=env_api_key,
            base_url=os.getenv(
                "DEEPSEEK_BASE_URL",
                "https://api.deepseek.com",
            ).strip() or "https://api.deepseek.com",
        )

    config_path = Path(__file__).resolve().parent / "deepseek.local.json"
    if not config_path.exists():
        raise ValueError(
            "DeepSeek credentials are missing. Set DEEPSEEK_API_KEY or copy "
            "deepseek.local.example.json to deepseek.local.json and set api_key."
        )

    with config_path.open("r", encoding="utf-8") as file:
        config = DeepSeekLocalConfig.model_validate(json.load(file))

    if not config.api_key.strip():
        raise ValueError("DeepSeek api_key cannot be empty")
    return config


def validate_config() -> None:
    """
    启动时校验配置。
    """
    try:
        settings = get_settings()

        if not settings.bigmodel_api_key:
            raise ValueError("BIGMODEL_API_KEY is required but not set")

        if not settings.bigmodel_base_url:
            raise ValueError("BIGMODEL_BASE_URL is required but not set")

    except Exception as e:
        raise ValueError(f"Configuration error: {str(e)}")
