from types import SimpleNamespace

import pytest

from config import get_deepseek_config
from services.mode_output_normalizer import StreamingTextReplacer
from services.zhipu_service import ZhipuService


class FakeCompletions:
    def __init__(self):
        self.request = None

    async def create(self, **kwargs):
        self.request = kwargs
        message = SimpleNamespace(content="ok")
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


class FakeClient:
    def __init__(self):
        self.chat = SimpleNamespace(completions=FakeCompletions())


def test_deepseek_model_whitelist():
    service = ZhipuService()

    assert service._resolve_model_name("deepseek", "deepseek-v4-flash") == "deepseek-v4-flash"
    assert service._resolve_model_name("deepseek", "deepseek-v4-pro") == "deepseek-v4-pro"

    with pytest.raises(ValueError, match="Unsupported DeepSeek model"):
        service._resolve_model_name("deepseek", "deepseek-chat")


def test_provider_whitelist():
    assert ZhipuService._resolve_provider("zhipu") == "zhipu"
    assert ZhipuService._resolve_provider("deepseek") == "deepseek"
    assert ZhipuService._resolve_provider(None) == "deepseek"

    with pytest.raises(ValueError, match="Unsupported model provider"):
        ZhipuService._resolve_provider("unknown")


def test_deepseek_and_interview_defaults_are_intentionally_distinct():
    service = ZhipuService()

    assert service._resolve_model_name("deepseek", None) == "deepseek-v4-flash"
    assert service.INTERVIEW_PROVIDER == "deepseek"
    assert service.INTERVIEW_MODEL == "deepseek-v4-pro"


def test_reasoning_prompt_mode_loads_uploaded_prompt_instead_of_professional():
    reasoning_prompt = ZhipuService._load_chat_system_prompt("reasoning")
    professional_prompt = ZhipuService._load_chat_system_prompt("professional")

    assert "XZM 技术推演与面试专家" in reasoning_prompt
    assert "第一性原理" in reasoning_prompt
    assert reasoning_prompt != professional_prompt


def test_reasoning_mode_contract_is_system_priority_and_history_is_data_only(monkeypatch):
    service = ZhipuService()
    monkeypatch.setattr(
        service,
        "_inject_rag_context",
        lambda message, system_prompt: system_prompt,
    )
    old_history = (
        "<untrusted_conversation_history>\n"
        "AI: ### a) 常规八股回答\n旧的专业模式回答\n"
        "</untrusted_conversation_history>"
    )

    mode, prompt = service._compose_chat_system_prompt("请解释 CAS", "reasoning")
    context = service._prepare_conversation_context(old_history, mode)

    assert mode == "reasoning"
    assert "XZM 技术推演与面试专家" in prompt
    assert old_history not in prompt
    assert prompt.rstrip().endswith("</current_mode_contract>")
    assert "本次回答不得使用专业模式的四个固定标题" in prompt
    assert context.startswith(
        '<conversation_reference priority="data-only" format="json">'
    )
    assert "旧的专业模式回答" in context
    assert "### a) 常规八股回答" not in context
    assert "[旧模式标题已省略]" in context


def test_unknown_prompt_mode_is_normalized_before_prompt_composition(monkeypatch):
    service = ZhipuService()
    monkeypatch.setattr(
        service,
        "_inject_rag_context",
        lambda message, system_prompt: system_prompt,
    )

    mode, prompt = service._compose_chat_system_prompt("hello", "unexpected")

    assert mode == "professional"
    assert prompt.rstrip().endswith("</current_mode_contract>")
    assert '<current_mode_contract mode="professional">' in prompt


def test_reasoning_heading_normalizer_handles_every_chunk_boundary():
    old_headings = (
        "### a) 常规八股回答",
        "###a) 常规八股回答",
        "### a)常规八股回答",
    )
    for old_heading in old_headings:
        expected = "前言\n### 1. 先给答案\n正文"
        for split_at in range(len(old_heading) + 1):
            normalizer = StreamingTextReplacer(
                ZhipuService.REASONING_HEADING_REPLACEMENTS
            )
            chunks = [
                "前言\n" + old_heading[:split_at],
                old_heading[split_at:] + "\n正文",
            ]
            actual = "".join(normalizer.feed(chunk) for chunk in chunks)
            actual += normalizer.flush()
            assert actual == expected


def test_non_reasoning_heading_normalizer_is_noop():
    normalizer = StreamingTextReplacer({})
    value = "### a) 常规八股回答"

    assert normalizer.feed(value) == value
    assert normalizer.flush() == ""


def test_deepseek_environment_credentials_take_precedence(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-environment-key")
    monkeypatch.setenv("DEEPSEEK_BASE_URL", "https://example.invalid")
    get_deepseek_config.cache_clear()

    try:
        config = get_deepseek_config()
        assert config.api_key == "test-environment-key"
        assert config.base_url == "https://example.invalid"
    finally:
        get_deepseek_config.cache_clear()


@pytest.mark.asyncio
async def test_deepseek_thinking_omits_temperature(monkeypatch):
    service = ZhipuService()
    client = FakeClient()
    monkeypatch.setattr(service, "_build_client", lambda provider: client)

    result = await service._call_non_stream(
        [{"role": "user", "content": "hello"}],
        provider="deepseek",
        model_name="deepseek-v4-flash",
        enable_thinking=True,
    )

    assert result == "ok"
    assert client.chat.completions.request["model"] == "deepseek-v4-flash"
    assert client.chat.completions.request["extra_body"] == {
        "thinking": {"type": "enabled"}
    }
    assert client.chat.completions.request["reasoning_effort"] == "high"
    assert "temperature" not in client.chat.completions.request


@pytest.mark.asyncio
async def test_non_thinking_request_keeps_temperature(monkeypatch):
    service = ZhipuService()
    client = FakeClient()
    monkeypatch.setattr(service, "_build_client", lambda provider: client)

    await service._call_non_stream(
        [{"role": "user", "content": "hello"}],
        provider="deepseek",
        model_name="deepseek-v4-pro",
        enable_thinking=False,
    )

    assert client.chat.completions.request["temperature"] == service.settings.temperature
