"""Responsive browser smoke test with deterministic API fixtures.

Run against a local Vite server by default. Screenshots are written to
``frontend/test-artifacts/audit`` for manual visual review.
"""

from pathlib import Path
import json
import os

from playwright.sync_api import sync_playwright


OUTPUT = Path(__file__).resolve().parent.parent / "test-artifacts" / "audit"
OUTPUT.mkdir(parents=True, exist_ok=True)
BASE_URL = os.environ.get("TEST_BASE_URL", "http://127.0.0.1:5173")

JOBS = [
    {
        "id": "job-1",
        "company": "星河云计算",
        "title": "2027 届校园招聘正式启动",
        "positions": "Java 后端、算法工程师、数据平台开发",
        "industry": "IT/互联网",
        "locations": "北京 / 上海 / 深圳",
        "recruitmentType": "秋招正式批",
        "targetGraduates": "2027 届毕业生",
        "deadline": "2026-09-30",
        "publishedDate": "2026-08-12",
        "firstSeenAt": "2026-08-12T12:00:00Z",
        "sourceKind": "OFFICIAL",
        "sourceName": "星河招聘官网",
        "announcementUrl": "https://example.com/announcement",
        "applyUrl": "https://example.com/apply",
    },
    {
        "id": "job-2",
        "company": "开源智造",
        "title": "研发岗位提前批",
        "positions": "嵌入式、芯片验证、测试开发",
        "industry": "硬件/半导体",
        "locations": "杭州 / 苏州",
        "recruitmentType": "提前批",
        "targetGraduates": "本硕博",
        "deadline": "以公告为准",
        "publishedDate": "2026-08-11",
        "firstSeenAt": "2026-08-11T12:00:00Z",
        "sourceKind": "WECHAT",
        "sourceName": "微信公众号公开文章",
        "announcementUrl": "https://example.com/wechat",
        "applyUrl": "https://example.com/apply-2",
    },
]

APPLICATIONS = [
    {
        "id": "application-1",
        "company": "星河云计算",
        "applyUrl": "https://example.com/apply",
        "roleName": "Java 后端工程师",
        "status": "APPLIED",
        "location": "北京",
        "deadline": "2026-09-30",
        "nextAction": "复习 MySQL 索引",
        "nextActionAt": "2026-08-15T20:00:00",
        "notes": "官网投递",
        "updatedAt": "2026-08-14T09:20:00Z",
    },
    {
        "id": "application-2",
        "company": "开源智造",
        "applyUrl": "https://example.com/apply-2",
        "roleName": "测试开发工程师",
        "status": "INTERVIEW_1",
        "location": "杭州",
        "nextAction": "准备项目介绍",
        "nextActionAt": "2026-08-16T10:00:00",
        "updatedAt": "2026-08-13T12:00:00Z",
    },
]

DOCUMENTS = [
    {
        "id": "knowledge-1",
        "title": "Java 后端岗位说明",
        "sourceType": "CAREER_CONTEXT",
        "contentChars": 1260,
        "updatedAt": "2026-08-12T11:00:00Z",
    }
]

PROBLEMS = [
    {
        "slug": "two-sum",
        "frontendId": "1",
        "title": "两数之和",
        "difficulty": "EASY",
        "timeLimitMinutes": 20,
        "sources": ["HOT100", "CODETOP"],
        "judgeable": True,
    }
]

PROBLEM_DETAIL = {
    **PROBLEMS[0],
    "contentHtml": (
        "<h2>题目</h2><p>给定一个整数数组和目标值，返回和为目标值的两个下标。</p>"
        "<h3>要求</h3><p>请分析时间复杂度与空间复杂度。</p>"
    ),
    "sampleTestCase": "nums = [2,7,11,15], target = 9",
    "tags": ["数组", "哈希表"],
    "codeTemplates": {
        "java": (
            "class Solution {\n"
            "    public int[] twoSum(int[] nums, int target) {\n"
            "        return new int[0];\n"
            "    }\n"
            "}"
        )
    },
}


def api_body(url: str, method: str) -> tuple[int, dict]:
    if "/algorithm/problems/two-sum" in url:
        return 200, PROBLEM_DETAIL
    if "/algorithm/problems" in url:
        return 200, PROBLEMS
    if "/algorithm/submissions" in url:
        return 200, []
    if "/api/recruitments/facets" in url:
        return 200, {
            "code": 200,
            "data": {
                "cities": [{"value": "北京"}, {"value": "上海"}],
                "recruitmentTypes": [{"value": "秋招正式批"}],
                "companyTypes": [{"value": "民营企业"}],
                "sourceKinds": [{"value": "OFFICIAL"}, {"value": "WECHAT"}],
            },
        }
    if "/api/recruitments" in url:
        return 200, {
            "code": 200,
            "data": {
                "items": JOBS,
                "total": 778,
                "page": 1,
                "size": 30,
                "summary": {
                    "total": 778,
                    "newToday": 49,
                    "newWeek": 192,
                    "sourceCount": 81,
                    "running": False,
                    "lastUpdated": "2026-08-12T19:36:00Z",
                },
            },
        }
    if "/api/applications" in url:
        return 200, {
            "code": 200,
            "data": {
                "items": APPLICATIONS,
                "summary": {"upcomingReminders": 2, "OFFER": 0},
            },
        }
    if "/api/knowledge" in url:
        return 200, {"code": 200, "data": DOCUMENTS}
    if "/api/media/ocr" in url and method == "POST":
        return 200, {"code": 200, "data": {"text": "MySQL 使用 B+ 树索引。"}}
    if "/interview-agent/sessions" in url:
        return 200, []
    if "/chat/" in url or "/history" in url:
        return 200, {"code": 200, "data": {"records": [], "hasMore": False}}
    return 200, {"code": 200, "data": {"items": [], "records": [], "summary": {}}}


def install_fixtures(page) -> None:
    page.add_init_script(
        """
        localStorage.setItem('token', 'visual-audit-token');
        localStorage.setItem('userInfo', JSON.stringify({
          userId: 7, username: 'xzm', userType: '普通用户'
        }));
        class AuditRecognition {
          start() {
            setTimeout(() => {
              const result = [{ transcript: '语音识别验收文本' }];
              result.isFinal = true;
              this.onresult?.({ resultIndex: 0, results: [result] });
            }, 20);
          }
          stop() { this.onend?.(); }
        }
        window.SpeechRecognition = AuditRecognition;
        window.webkitSpeechRecognition = AuditRecognition;
        """
    )

    def route_api(route):
        status, body = api_body(route.request.url, route.request.method)
        route.fulfill(
            status=status,
            content_type="application/json; charset=utf-8",
            body=json.dumps(body, ensure_ascii=False),
        )

    page.route("**/xzm/**", route_api)


def assert_page(page, path: str, expected: str, screenshot_name: str) -> None:
    page.goto(f"{BASE_URL}{path}", wait_until="networkidle")
    page.get_by_text(expected, exact=False).first.wait_for(state="visible")
    overflow = page.evaluate(
        """() => ({
          viewport: document.documentElement.clientWidth,
          document: document.documentElement.scrollWidth,
          body: document.body.scrollWidth
        })"""
    )
    assert overflow["document"] <= overflow["viewport"] + 1, (path, overflow)
    assert overflow["body"] <= overflow["viewport"] + 1, (path, overflow)
    page.screenshot(path=str(OUTPUT / screenshot_name), full_page=True)


with sync_playwright() as playwright:
    browser_executable = os.environ.get("PLAYWRIGHT_BROWSER_EXECUTABLE")
    launch_options = {"headless": True}
    if browser_executable:
        launch_options["executable_path"] = browser_executable
    browser = playwright.chromium.launch(**launch_options)
    all_errors: list[str] = []

    routes = [
        ("chat", "/chat", "你好，xzm"),
        ("interview", "/aiInterview", "模拟面试工作台"),
        ("algorithm", "/algorithms", "两数之和"),
        ("recruitment", "/recruitment", "招聘信息汇总"),
        ("applications", "/applications", "投递记录"),
        ("knowledge", "/knowledge", "只让 AI 读取"),
    ]

    for viewport_name, viewport in (
        ("desktop", {"width": 1440, "height": 960}),
        ("mobile", {"width": 390, "height": 844}),
    ):
        page = browser.new_page(viewport=viewport, device_scale_factor=1)
        errors: list[str] = []
        page.on("pageerror", lambda error: errors.append(f"pageerror: {error}"))
        page.on(
            "console",
            lambda message: errors.append(f"console-error: {message.text}")
            if message.type == "error"
            else None,
        )
        install_fixtures(page)
        for name, path, expected in routes:
            assert_page(page, path, expected, f"{viewport_name}-{name}.png")
            if viewport_name == "desktop":
                sidebar = page.locator(".gemini-sidebar")
                sidebar.wait_for(state="visible")
                assert sidebar.get_attribute("aria-hidden") is None
            elif name in {"recruitment", "applications", "knowledge"}:
                page.locator(".workspace-frame__menu").wait_for(state="visible")

        if viewport_name == "desktop":
            page.goto(f"{BASE_URL}/chat", wait_until="networkidle")
            assert page.locator(".mode-copy small").count() == 1
            page.locator(".workspace-density-toggle").click()
            assert page.locator(".mode-copy small").count() == 6
            page.locator(".workspace-density-toggle").click()

            page.get_by_title("收起侧边栏").click()
            page.wait_for_function(
                "() => Math.round(document.querySelector('.gemini-sidebar').getBoundingClientRect().width) === 64"
            )
            positions = page.locator(".mode-btn").evaluate_all(
                "buttons => buttons.map(button => ({ x: Math.round(button.getBoundingClientRect().x), y: Math.round(button.getBoundingClientRect().y) }))"
            )
            assert len({position["x"] for position in positions}) == 1, positions
            assert all(
                positions[index]["y"] < positions[index + 1]["y"]
                for index in range(len(positions) - 1)
            ), positions
            page.get_by_title("展开侧边栏").click()

            page.get_by_role("button", name="秋招信息").click()
            page.wait_for_url(f"{BASE_URL}/recruitment")
            page.locator(".workspace-frame__topbar").wait_for(state="visible")
            page.get_by_role("button", name="投递追踪").click()
            page.wait_for_url(f"{BASE_URL}/applications")
            page.locator(".workspace-frame__topbar").wait_for(state="visible")
            assert page.locator(".application-row").count() == 2
            assert page.locator(".pipeline").count() == 0
            assert "status--blue" in (page.locator(".status-control").first.get_attribute("class") or "")

            page.get_by_role("button", name="＋ 新增投递").click()
            dialog = page.locator(".application-dialog")
            dialog.wait_for(state="visible")
            assert dialog.locator("input[required]").count() == 2
            assert dialog.get_by_text("公司名和投递链接为必填项").count() == 0
            dialog.get_by_role("button", name="关闭").click()

            page.goto(f"{BASE_URL}/chat", wait_until="networkidle")
            page.locator(".user-avatar-btn").click()
            menu = page.locator(".user-menu")
            menu.wait_for(state="visible")
            menu_layer = menu.evaluate(
                "element => ({ position: getComputedStyle(element).position, z: Number(getComputedStyle(element).zIndex) })"
            )
            assert menu_layer["position"] == "fixed", menu_layer
            assert menu_layer["z"] >= 12000, menu_layer
            page.locator(".user-menu .logout").click()
            message_box = page.locator(".el-message-box")
            message_box.wait_for(state="visible")
            box = message_box.bounding_box()
            assert box is not None
            assert abs((box["x"] + box["width"] / 2) - viewport["width"] / 2) <= 2, box
            assert abs((box["y"] + box["height"] / 2) - viewport["height"] / 2) <= 2, box
            overlay_z = page.locator(".el-overlay.is-message-box").evaluate(
                "element => Number(getComputedStyle(element).zIndex)"
            )
            assert overlay_z >= 12010, overlay_z
            page.get_by_role("button", name="取消").click()

            page.goto(f"{BASE_URL}/chat", wait_until="networkidle")
            page.locator('input[type="file"][accept^="image/"]').set_input_files(
                {
                    "name": "mysql.png",
                    "mimeType": "image/png",
                    "buffer": b"not-a-real-image-because-the-api-is-mocked",
                }
            )
            preview = page.get_by_label("可编辑的图片识别文本")
            preview.wait_for(state="visible")
            assert preview.input_value() == "MySQL 使用 B+ 树索引。"
            preview.fill("MySQL 使用聚簇 B+ 树索引。")
            assert preview.input_value() == "MySQL 使用聚簇 B+ 树索引。"

            page.get_by_title("点击开始录音").click()
            prompt = page.locator(".prompt-input textarea")
            prompt.wait_for(state="visible")
            page.wait_for_function(
                "() => document.querySelector('.prompt-input textarea')?.value.includes('语音识别验收文本')"
            )
            assert "语音识别验收文本" in prompt.input_value()
            page.screenshot(path=str(OUTPUT / "desktop-chat-ocr-voice.png"), full_page=True)
        else:
            page.goto(f"{BASE_URL}/knowledge", wait_until="networkidle")
            page.locator(".workspace-frame__menu").click()
            page.locator(".gemini-sidebar.expanded").wait_for(state="visible")
            assert page.evaluate(
                "() => Boolean(document.elementFromPoint(24, 24)?.closest('.gemini-sidebar'))"
            )
            assert page.locator(".workspace-frame__body").evaluate(
                "element => Math.round(element.getBoundingClientRect().x)"
            ) == 0
            page.screenshot(path=str(OUTPUT / "mobile-knowledge-sidebar-open.png"), full_page=True)

        all_errors.extend(f"{viewport_name}: {error}" for error in errors)
        page.close()

    assert not all_errors, "\n".join(all_errors)
    browser.close()

print(f"Visual audit passed; screenshots: {OUTPUT}")
