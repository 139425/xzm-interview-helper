"""
提示词模板管理服务。
使用 LangChain PromptTemplate 管理所有提示词模板。
"""

import os
import logging
from functools import lru_cache

from langchain_core.prompts import ChatPromptTemplate

logger = logging.getLogger(__name__)

# 提示词文件根目录
PROMPTS_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "prompts")


def _load_prompt_file(relative_path: str) -> str:
    """从文件加载提示词内容。"""
    file_path = os.path.join(PROMPTS_DIR, relative_path)
    with open(file_path, "r", encoding="utf-8") as f:
        return f.read().strip()


class PromptService:
    """使用 LangChain PromptTemplate 管理所有提示词模板。"""

    def __init__(self):
        # 预加载所有提示词文件
        self._interviewer_role = _load_prompt_file("interview/interviewer_role.txt")
        self._evaluator_role = _load_prompt_file("interview/evaluator_role.txt")
        self._summarizer_role = _load_prompt_file("interview/summarizer_role.txt")
        self._generate_questions_tpl = _load_prompt_file("interview/generate_questions.txt")
        self._evaluate_answer_tpl = _load_prompt_file("interview/evaluate_answer.txt")
        self._generate_summary_tpl = _load_prompt_file("interview/generate_summary.txt")
        self._chat_system_prompt = _load_prompt_file("chat/system_prompt.txt")
        logger.info("PromptService 初始化完成，已加载所有提示词模板")

    def get_generate_questions_messages(self, resume_text: str) -> list[dict]:
        """构建生成面试问题的消息列表。"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", "{system}"),
            ("human", "{resume_text}\n\n{template}"),
        ])
        messages = prompt.format_messages(
            system=self._interviewer_role,
            resume_text=resume_text,
            template=self._generate_questions_tpl,
        )
        return [{"role": m.type if m.type != "human" else "user", "content": m.content} for m in messages]

    def get_evaluate_answer_messages(self, question: str, answer: str) -> list[dict]:
        """构建评价回答的消息列表。"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", "{system}"),
            ("human", "面试问题：{question}\n\n用户回答：{answer}\n\n{template}"),
        ])
        messages = prompt.format_messages(
            system=self._evaluator_role,
            question=question,
            answer=answer,
            template=self._evaluate_answer_tpl,
        )
        return [{"role": m.type if m.type != "human" else "user", "content": m.content} for m in messages]

    def get_generate_summary_messages(self, record: str) -> list[dict]:
        """构建生成总结的消息列表。"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", "{system}"),
            ("human", "{template}"),
        ])
        # generate_summary.txt 中包含 {record} 占位符，先替换
        filled_template = self._generate_summary_tpl.replace("{record}", record)
        messages = prompt.format_messages(
            system=self._summarizer_role,
            template=filled_template,
        )
        return [{"role": m.type if m.type != "human" else "user", "content": m.content} for m in messages]

    @property
    def chat_system_prompt(self) -> str:
        """获取对话系统提示词。"""
        return self._chat_system_prompt


# 单例
_prompt_service: PromptService | None = None


def get_prompt_service() -> PromptService:
    """获取或创建 PromptService 单例。"""
    global _prompt_service
    if _prompt_service is None:
        _prompt_service = PromptService()
    return _prompt_service
