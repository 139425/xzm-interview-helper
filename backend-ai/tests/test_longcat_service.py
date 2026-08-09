"""
智谱服务单元测试。
"""

import pytest
from services.zhipu_service import ZhipuService
from langchain_core.messages import AIMessageChunk


class TestSseTextIntegrity:
    """SSE can carry spaces directly and must preserve literal placeholder text."""
    
    def test_encode_spaces_basic(self):
        """基本空格编码。"""
        result = ZhipuService.encode_for_sse("hello world")
        assert result == "hello world"
    
    def test_encode_multiple_spaces(self):
        """多空格编码。"""
        result = ZhipuService.encode_for_sse("a b c d")
        assert result == "a b c d"
    
    def test_encode_no_spaces(self):
        """无空格不变。"""
        result = ZhipuService.encode_for_sse("helloworld")
        assert result == "helloworld"
    
    def test_encode_empty_string(self):
        """空字符串。"""
        result = ZhipuService.encode_for_sse("")
        assert result == ""
    
    def test_encode_none(self):
        """None 转为空字符串。"""
        result = ZhipuService.encode_for_sse(None)
        assert result == ""
    
    def test_decode_spaces_basic(self):
        """Literal legacy placeholder text is ordinary model content."""
        result = ZhipuService.decode_from_sse("hello{{SP}}world")
        assert result == "hello{{SP}}world"
    
    def test_decode_multiple_spaces(self):
        """多空格解码。"""
        result = ZhipuService.decode_from_sse("a{{SP}}b{{SP}}c{{SP}}d")
        assert result == "a{{SP}}b{{SP}}c{{SP}}d"
    
    def test_decode_no_encoded_spaces(self):
        """无占位符不变。"""
        result = ZhipuService.decode_from_sse("helloworld")
        assert result == "helloworld"
    
    def test_decode_empty_string(self):
        """空字符串。"""
        result = ZhipuService.decode_from_sse("")
        assert result == ""
    
    def test_decode_none(self):
        """None 转为空字符串。"""
        result = ZhipuService.decode_from_sse(None)
        assert result == ""
    
    def test_round_trip(self):
        """编码再解码应回到原文。"""
        original = "This is a test message with spaces"
        encoded = ZhipuService.encode_for_sse(original)
        decoded = ZhipuService.decode_from_sse(encoded)
        assert decoded == original


class TestContentMarkers:
    """标记常量测试。"""
    
    def test_thinking_marker(self):
        """思考标记。"""
        assert ZhipuService.THINKING_MARKER == "[THINKING]"
    
    def test_content_marker(self):
        """内容标记。"""
        assert ZhipuService.CONTENT_MARKER == "[CONTENT]"
    
    def test_done_marker(self):
        """完成标记。"""
        assert ZhipuService.DONE_MARKER == "[DONE]"
    
    def test_error_marker(self):
        """错误标记。"""
        assert ZhipuService.ERROR_MARKER == "[ERROR]"


class TestFormatChunk:
    """流式分片格式化测试。"""
    
    @pytest.fixture
    def service(self, monkeypatch):
        """创建服务实例。"""
        monkeypatch.setenv("BIGMODEL_API_KEY", "test-api-key")
        monkeypatch.setenv("BIGMODEL_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/")
        monkeypatch.setenv("BIGMODEL_MODEL_NAME", "glm-4.7-flashx")
        from config import get_settings
        get_settings.cache_clear()
        return ZhipuService()
    
    def test_format_reasoning_chunk(self, service):
        """思考内容应带 THINKING 标记。"""
        chunk = AIMessageChunk(
            content="",
            additional_kwargs={"reasoning_content": "thinking text"}
        )
        result = service._format_chunk(chunk)
        assert result == "[THINKING]thinking text"
    
    def test_format_content_chunk(self, service):
        """回答内容应带 CONTENT 标记。"""
        chunk = AIMessageChunk(
            content="answer text",
            additional_kwargs={}
        )
        result = service._format_chunk(chunk)
        assert result == "[CONTENT]answer text"
    
    def test_format_empty_chunk(self, service):
        """空分片应返回 None。"""
        chunk = AIMessageChunk(
            content="",
            additional_kwargs={}
        )
        result = service._format_chunk(chunk)
        assert result is None


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
