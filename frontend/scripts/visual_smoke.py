from pathlib import Path
import os

from playwright.sync_api import sync_playwright


OUTPUT = Path(__file__).resolve().parent.parent / "test-artifacts"
OUTPUT.mkdir(exist_ok=True)
BASE_URL = os.environ.get("TEST_BASE_URL", "http://127.0.0.1:5173")

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1600, "height": 1000}, device_scale_factor=1)
    errors = []
    page.on("pageerror", lambda error: errors.append(f"pageerror:{error}"))
    page.route("**/xzm/**", lambda route: route.fulfill(
        status=200,
        content_type="application/json",
        body='{"code":200,"message":"Success","data":{"items":[],"records":[],"summary":{},"total":0}}',
    ))

    for name, path, expected in [
        ("chat", "/chat", "AI 助手"),
        ("recruitment", "/recruitment", "招聘信息汇总"),
        ("applications", "/applications", "每一次投递"),
        ("knowledge", "/knowledge", "只让 AI 读取"),
    ]:
        page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded")
        page.get_by_text(expected, exact=False).first.wait_for(state="visible")
        page.screenshot(path=str(OUTPUT / f"{name}.png"), full_page=True)

    assert not errors, "\n".join(errors)
    browser.close()
