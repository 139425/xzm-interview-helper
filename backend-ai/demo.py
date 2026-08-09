from openai import OpenAI

client = OpenAI(
    api_key="66739c3570454ca5a344ee005fffbb1e.j0CpHV4o7OKHlhha",
    base_url="https://open.bigmodel.cn/api/paas/v4/",
)

response = client.chat.completions.create(
    model="GLM-4.7-Flash",
    messages=[{"role": "user", "content": "你好，介绍一下你自己"}],
)

print(response.choices[0].message.content)
