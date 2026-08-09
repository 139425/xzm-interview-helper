"""
Python AI 后端测试的 Pytest 配置与固定件。
"""

import pytest
import sys
import os

# 将项目根目录加入路径，便于导入
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture(autouse=True)
def setup_test_env(monkeypatch):
    """设置测试环境变量。"""
    # 设置测试必需的环境变量
    monkeypatch.setenv("BIGMODEL_API_KEY", "test-api-key-for-testing")
    monkeypatch.setenv("BIGMODEL_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/")
    monkeypatch.setenv("BIGMODEL_MODEL_NAME", "glm-4.7-flashx")
    monkeypatch.setenv("PORT", "9090")
    
    # 每次测试前清空缓存配置
    from config import get_settings
    get_settings.cache_clear()
    
    yield
    
    # 测试后清空缓存配置
    get_settings.cache_clear()
