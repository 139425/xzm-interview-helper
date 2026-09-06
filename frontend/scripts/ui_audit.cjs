/** Browser adversarial audit. Run against Vite: NODE_PATH=<playwright modules> node scripts/ui_audit.cjs */
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const fixture = require("./ui-fixtures.json");
const base = process.env.TEST_BASE_URL || "http://127.0.0.1:5173";
const output = path.resolve(__dirname, "../test-results/ui-audit");
fs.mkdirSync(output, { recursive: true });
const wrap = (data) => ({ code: 200, data });
const history = Array.from({ length: 18 }, (_, i) => ({
  memoryId: i + 1,
  conversationId: `audit-${i + 1}`,
  lastQuestion: [
    "如何理解 MySQL 的索引机制？",
    "模拟一轮 Java 后端面试",
    "项目经历如何讲得更有说服力",
  ][i % 3],
  lastChatTime: "2026-09-05T12:00:00",
  messageCount: 6,
}));
function api(url, method) {
  if (url.includes("/record/histories"))
    return url.includes("/page")
      ? { records: history, hasMore: false, total: history.length }
      : history;
  if (url.includes("/record/history")) return [];
  if (url.includes("/record/conversations"))
    return { memoryId: 900, conversationId: "audit-live" };
  if (url.includes("/algorithm/problems/two-sum"))
    return fixture.PROBLEM_DETAIL;
  if (url.includes("/algorithm/problems")) return fixture.PROBLEMS;
  if (url.includes("/algorithm/submissions")) return [];
  if (url.includes("/api/recruitments/facets"))
    return wrap({
      cities: [{ value: "北京" }],
      recruitmentTypes: [],
      companyTypes: [],
      sourceKinds: [],
    });
  if (url.includes("/api/recruitments"))
    return wrap({
      items: fixture.JOBS,
      total: 778,
      page: 1,
      size: 30,
      summary: {
        total: 778,
        newToday: 49,
        newWeek: 192,
        sourceCount: 81,
        running: false,
        lastUpdated: "2026-09-05T12:00:00",
      },
    });
  if (url.includes("/api/applications"))
    return wrap({
      items: fixture.APPLICATIONS,
      summary: { upcomingReminders: 2 },
    });
  if (url.includes("/api/assessments")) return wrap({ items: [], summary: {} });
  if (url.includes("/api/knowledge")) return wrap(fixture.DOCUMENTS);
  if (url.includes("/interview-agent/sessions")) return [];
  if (url.includes("/admin/server-agent/status"))
    return wrap({
      agentEnabled: true,
      hostname: "audit-host",
      executionUser: "www",
      uptimeSeconds: 284400,
      cpuLoad: 0.18,
      heapUsedBytes: 42000000,
      heapMaxBytes: 100000000,
      disk: [{ path: "/", totalBytes: 100000000, freeBytes: 64000000 }],
      capabilities: {
        command: true,
        readFile: true,
        writeFile: true,
        createSite: true,
        serviceStatus: true,
        serviceRestart: false,
      },
      limits: { maxAgentSteps: 8 },
    });
  if (url.includes("/admin/users")) return wrap([]);
  if (url.includes("/verification/config"))
    return wrap({ registrationMode: "email" });
  if (url.includes("/verification/slider"))
    return wrap({ challengeId: "audit-slider" });
  if (url.includes("captcha"))
    return wrap({
      captchaId: "audit",
      imageDataUrl:
        "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNTAiIGhlaWdodD0iNDQiPjx0ZXh0IHg9IjEwIiB5PSIzMCI+QTdIODwvdGV4dD48L3N2Zz4=",
    });
  return wrap({ items: [], records: [], summary: {} });
}
async function seed(page) {
  await page.addInitScript(() => {
    if (["/login", "/register"].includes(location.pathname)) {
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      return;
    }
    localStorage.setItem("token", "ui-audit-only");
    sessionStorage.setItem(
      "interviewReport",
      JSON.stringify({
        generatedAt: "2026-09-05T12:00:00",
        summary: "## 本次复盘\n\n项目表达清楚，可以继续补强数据库和并发知识。",
        rounds: [
          {
            question: "如何设计索引？",
            answer: "根据查询条件建立合适的索引。",
            score: 8,
            evaluation: "表达清楚，可补充执行计划的验证过程。",
            reference: "结合查询场景和执行计划说明。",
          },
        ],
      }),
    );
    localStorage.setItem(
      "userInfo",
      JSON.stringify({ userId: 7, username: "xzm", userType: "管理员" }),
    );
    if (!localStorage.getItem("theme")) localStorage.setItem("theme", "light");
    const originalFetch = window.fetch.bind(window);
    window.fetch = async (url, options) => {
      if (!String(url).includes("streamChat"))
        return originalFetch(url, options);
      const encoder = new TextEncoder();
      let timer,
        ended = false;
      const send = (controller, text) =>
        controller.enqueue(
          encoder.encode(
            text
              .split("\n")
              .map((line) => "data: " + line)
              .join("\n") + "\n\n",
          ),
        );
      return new Response(
        new ReadableStream({
          start(controller) {
            let count = 0;
            send(controller, "[CONTENT]## 流式验收\n\n");
            timer = setInterval(() => {
              if (ended) return;
              count++;
              send(
                controller,
                "[CONTENT]持续输出的一小段内容，保持阅读位置稳定。",
              );
              if (count >= 180) {
                ended = true;
                clearInterval(timer);
                send(controller, "[DONE]");
                controller.close();
              }
            }, 30);
            options?.signal?.addEventListener(
              "abort",
              () => {
                if (ended) return;
                ended = true;
                clearInterval(timer);
                controller.error(new DOMException("Aborted", "AbortError"));
              },
              { once: true },
            );
          },
          cancel() {
            ended = true;
            clearInterval(timer);
          },
        }),
        { headers: { "Content-Type": "text/event-stream" } },
      );
    };
  });
  await page.route("**/xzm/**", (route) =>
    route.fulfill({
      json: api(route.request().url(), route.request().method()),
    }),
  );
}
async function geometry(page, name) {
  const box = await page.evaluate(() => ({
    w: innerWidth,
    doc: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }));
  assert(
    box.doc <= box.w + 1 && box.body <= box.w + 1,
    `${name}: horizontal overflow ${JSON.stringify(box)}`,
  );
  const sidebar = page.locator(".gemini-sidebar");
  if (box.w > 768 && (await sidebar.count())) {
    const side = await sidebar.boundingBox();
    const body = await page
      .locator(
        ".workspace-frame__body, .xzm-chat-page__main, .agent-main, .algorithm-main",
      )
      .first()
      .boundingBox();
    if (body)
      assert(
        side.x + side.width <= body.x + 1,
        `${name}: sidebar overlaps content`,
      );
  }
  const mains = await page
    .locator("main, .workspace-frame__body")
    .evaluateAll((nodes) =>
      nodes.map((n) => ({
        width: n.clientWidth,
        scroll: n.scrollWidth,
        name: n.className,
      })),
    );
  for (const m of mains)
    assert(
      m.scroll <= m.width + 2,
      `${name}: overflowing main ${JSON.stringify(m)}`,
    );
}
async function screenshot(page, name) {
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
}
const answer =
  "## 先抓住索引的核心\n\n索引是一种帮助数据库减少扫描范围的数据结构。回答时可以从 **查找过程、存储结构、使用代价** 三个方面展开。\n\n### 为什么使用 B+ 树\n\n- 非叶子节点只存储索引键，单页能容纳更多键。\n- 叶子节点按顺序连接，适合范围查询。\n- 树高较低，减少磁盘读取次数。\n\n| 场景 | 建议 | 原因 |\n| --- | --- | --- |\n| 等值查询 | 使用高区分度索引 | 减少扫描行数 |\n| 范围查询 | 注意联合索引顺序 | 利用有序访问 |\n\n```sql\nSELECT id, name\nFROM candidates\nWHERE graduation_year = 2027\nORDER BY id LIMIT 20;\n```\n\n> 面试中可以结合执行计划，说明索引如何减少实际访问的数据量。\n\n";
async function seedMessages(page) {
  await page.evaluate(async (answer) => {
    const { useChatStore } = await import("/src/stores/chat.js");
    const { useUIStore } = await import("/src/stores/ui.js");
    const app = document.querySelector("#app").__vue_app__;
    const pinia = app.config.globalProperties.$pinia;
    const store = useChatStore(pinia);
    store.currentMemoryId = 900;
    store.messages = Array.from({ length: 6 }, (_, i) => [
      {
        id: `q${i}`,
        role: "user",
        content: `${i + 1}. 如何理解 MySQL 的索引机制？`,
      },
      {
        id: `a${i}`,
        role: "assistant",
        content: answer,
        thinkingContent: "先明确数据结构与查询模式，再结合例子说明。",
      },
    ]).flat();
    useUIStore(pinia).movePromptBarToBottom();
  }, answer);
  await page.locator(".xzm-msg--assistant").first().waitFor();
  await page.waitForTimeout(350);
}
async function runAudit() {
  const executablePath =
    process.env.PLAYWRIGHT_BROWSER_EXECUTABLE ||
    (fs.existsSync(
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    )
      ? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
      : undefined);
  const browser = await chromium.launch({ headless: true, executablePath });
  const errors = [];
  try {
    for (const viewport of process.env.UI_AUDIT_SCOPE === "editors"
      ? []
      : [
          { width: 1440, height: 960 },
          { width: 1024, height: 768 },
          { width: 390, height: 844 },
          { width: 320, height: 720 },
        ]) {
      const page = await browser.newPage({ viewport });
      await seed(page);
      page.on("pageerror", (err) =>
        errors.push(`${viewport.width}: ${err.message}`),
      );
      for (const [name, url, selector] of [
        ["chat", "/chat", ".xzm-welcome"],
        ["interview", "/aiInterview", ".intake-card"],
        ["algorithm", "/algorithms", ".workbench"],
        ["recruitment", "/recruitment", ".jobs-results"],
        ["applications", "/applications", ".sheet-card"],
        ["schedule", "/applications/schedule", ".schedule-main"],
        ["knowledge", "/knowledge", ".documents-card"],
        ["server", "/admin/server", ".host-plate"],
        ["users", "/admin/users", ".table-section"],
        ["report", "/interview-report", ".report-cover"],
        ["login", "/login", ".auth-card"],
        ["register", "/register", ".auth-card"],
      ]) {
        console.log(`CHECK ${viewport.width} ${name}`);
        await page.goto(base + url, { waitUntil: "networkidle" });
        await page.locator(selector).waitFor();
        await page.waitForTimeout(200);
        await geometry(page, `${viewport.width}-${name}`);
        await screenshot(page, `${viewport.width}-${name}`);
      }
      await page.goto(base + "/chat", { waitUntil: "networkidle" });
      if (viewport.width > 768) {
        assert.equal(await page.locator(".mode-btn").count(), 3);
        await page.getByTitle("收起侧边栏").click();
        await page.waitForTimeout(300);
        assert.equal(
          Math.round(
            (await page.locator(".gemini-sidebar").boundingBox()).width,
          ),
          64,
        );
        await page.getByTitle("展开侧边栏").click();
        await page.waitForTimeout(300);
      } else {
        await page
          .getByRole("button", { name: "展开侧边栏", exact: true })
          .last()
          .click();
        await page.waitForTimeout(300);
        assert(
          await page.locator(".xzm-chat-page__main").evaluate((el) => el.inert),
        );
        await page.keyboard.press("Escape");
        await page.waitForTimeout(200);
        assert(
          !(
            await page.locator(".gemini-sidebar").getAttribute("class")
          ).includes("expanded"),
        );
      }
      await page.getByRole("button", { name: "工具箱", exact: true }).click();
      await page.locator(".tools-menu").waitFor({ state: "visible" });
      await screenshot(page, `${viewport.width}-tools`);
      await page.keyboard.press("Escape");
      await page.mouse.click(viewport.width / 2, 180);
      const input = page.locator(".prompt-input textarea");
      await input.fill(
        "请解释 B+ 树\n给出一个 SQL 例子\n并比较联合索引\n最后总结适用场景\n再给出练习题",
      );
      const ib = await input.boundingBox();
      const wb = await page.locator(".xzm-welcome").boundingBox();
      assert(ib.y >= wb.y + wb.height, "welcome overlaps multiline input");
      await seedMessages(page);
      await page.getByRole("button", { name: "题目目录", exact: true }).click();
      await page.waitForTimeout(300);
      await screenshot(page, `${viewport.width}-conversation-outline`);
      if (viewport.width >= 1280) {
        const main = await page.locator(".xzm-chat-page__main").boundingBox(),
          outline = await page.locator(".xzm-question-nav").boundingBox();
        assert(
          main.x + main.width <= outline.x + 1,
          "docked outline overlaps reading area",
        );
      }
      await page.getByPlaceholder("搜索题目").fill("不存在的题目");
      assert(await page.getByText("没有匹配的题目").isVisible());
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
      await input.fill("继续提问");
      const composer = await page.locator(".chat-composer").boundingBox(),
        messages = await page.locator(".xzm-chat-page__messages").boundingBox();
      assert(
        messages.y + messages.height <= composer.y + 1,
        "composer covers messages",
      );
      assert(
        composer.y + composer.height <= viewport.height + 1,
        "composer outside viewport",
      );
      await page.locator(".xzm-chat-page__messages").evaluate((el) => {
        el.scrollTop = 100;
      });
      await page.waitForTimeout(150);
      assert(
        await page.getByRole("button", { name: "查看最新回复" }).isVisible(),
      );
      const before = await page
        .locator(".xzm-chat-page__messages")
        .evaluate((el) => el.scrollTop);
      await page.evaluate(async () => {
        const { useChatStore } = await import("/src/stores/chat.js");
        const p =
          document.querySelector("#app").__vue_app__.config.globalProperties
            .$pinia;
        useChatStore(p).messages.at(-1).content += "\n\n新的回答内容。".repeat(
          30,
        );
      });
      await page.waitForTimeout(300);
      const after = await page
        .locator(".xzm-chat-page__messages")
        .evaluate((el) => el.scrollTop);
      assert(
        Math.abs(before - after) < 3,
        `new content stole scroll ${before} -> ${after}`,
      );
      await page.getByRole("button", { name: "查看最新回复" }).click();
      await page.waitForTimeout(500);
      await page.evaluate(() => {
        localStorage.setItem("theme", "dark");
        localStorage.setItem("xzm-theme", "dark");
        document.documentElement.dataset.theme = "dark";
        document.body.dataset.theme = "dark";
      });
      await page.waitForTimeout(350);
      await screenshot(page, `${viewport.width}-conversation-dark`);
      if (viewport.width === 1440) {
        await page.goto(base + "/chat", { waitUntil: "networkidle" });
        await input.fill("中文候选词");
        await input.dispatchEvent("keydown", {
          key: "Enter",
          code: "Enter",
          isComposing: true,
        });
        assert.equal(
          await page.locator(".xzm-msg--user").count(),
          0,
          "IME Enter accidentally sent a message",
        );
        await input.fill("请持续输出，测试阅读跟随");
        await input.press("Enter");
        await page
          .getByRole("button", { name: "停止生成", exact: true })
          .waitFor();
        await page.waitForTimeout(1800);
        assert(
          await input.isEnabled(),
          "cannot prepare a draft during generation",
        );
        await input.fill("下一条问题草稿");
        const flowing = await page
          .locator(".xzm-chat-page__messages")
          .evaluate((el) => ({
            gap: el.scrollHeight - el.scrollTop - el.clientHeight,
            height: el.scrollHeight,
          }));
        assert(
          flowing.gap < 110,
          `stream failed to follow ${JSON.stringify(flowing)}`,
        );
        await screenshot(page, "streaming-live");
        await page
          .locator(".xzm-chat-page__messages")
          .evaluate((el) => (el.scrollTop = 50));
        await page.waitForTimeout(120);
        const pinned = await page
          .locator(".xzm-chat-page__messages")
          .evaluate((el) => el.scrollTop);
        await page.waitForTimeout(700);
        assert(
          Math.abs(
            (await page
              .locator(".xzm-chat-page__messages")
              .evaluate((el) => el.scrollTop)) - pinned,
          ) < 3,
          "live stream stole reading position",
        );
        await page
          .getByRole("button", { name: "停止生成", exact: true })
          .click();
        await page
          .getByRole("button", { name: "停止生成", exact: true })
          .waitFor({ state: "hidden" });
        assert.equal(
          await input.inputValue(),
          "下一条问题草稿",
          "stop erased the next draft",
        );
        assert(
          (
            await page.locator(".xzm-msg--assistant").last().textContent()
          ).includes("持续输出"),
          "stop lost partial output",
        );
        await input.fill("第二次流式测试");
        await input.press("Enter");
        await page
          .getByRole("button", { name: "停止生成", exact: true })
          .waitFor();
        await page.waitForTimeout(150);
        await page.locator(".xzm-chat-page__messages").evaluate((el) => {
          el.scrollTop = 0;
          el.dispatchEvent(new Event("scroll"));
        });
        await page.getByRole("button", { name: "新对话", exact: true }).click();
        await page.waitForTimeout(300);
        assert.equal(
          await page.locator(".xzm-msg").count(),
          0,
          "discarded stream leaked into new conversation",
        );
        assert.equal(
          await page.locator(".chat-latest").count(),
          0,
          "new conversation kept the old reading position",
        );
        await page.getByRole("button", { name: "全部工作区", exact: true }).click();
        await page
          .getByRole("button", { name: "个人资料", exact: true })
          .click();
        await page.waitForURL("**/knowledge");
        await page.setViewportSize({ width: 390, height: 844 });
        await page.waitForTimeout(250);
        assert(
          await page.locator(".workspace-frame__menu").isVisible(),
          "resize listener lost after navigation",
        );
        await page.setViewportSize(viewport);
        await page.goto(base + "/chat", { waitUntil: "networkidle" });
        await seedMessages(page);
      }
      await page.emulateMedia({ reducedMotion: "reduce" });
      const anim = await page
        .locator(".xzm-msg")
        .first()
        .evaluate((el) => getComputedStyle(el).animationDuration);
      assert(parseFloat(anim) <= 0.01, `reduced motion ${anim}`);
      await page.close();
      console.log(
        `PASS ${viewport.width}: routes, panels, input, reading position, theme`,
      );
    }
    for (const viewport of [
      { width: 1440, height: 960 },
      { width: 390, height: 844 },
    ]) {
      const page = await browser.newPage({ viewport });
      await seed(page);
      await page.goto(base + "/html-preview.html", {
        waitUntil: "networkidle",
      });
      await page
        .locator("#source")
        .fill(
          '<h1>预览验证</h1><script>document.body.dataset.works="yes"<\/script>',
        );
      await page.waitForTimeout(350);
      assert.equal(
        await page.frameLocator("#preview").locator("h1").textContent(),
        "预览验证",
      );
      assert.equal(
        await page
          .frameLocator("#preview")
          .locator("body")
          .getAttribute("data-works"),
        "yes",
      );
      assert.equal(
        await page.locator("#preview").getAttribute("sandbox"),
        "allow-scripts",
      );
      await screenshot(page, `${viewport.width}-html-preview`);
      for (const [name, url, selector] of [
        ["resume", "resume_editor.html", ".editor"],
        ["code", "code-editor.html", "#toolbar"],
        ["markdown", "markdown-editor.html", "header"],
      ]) {
        await page.goto(base + "/" + url, { waitUntil: "domcontentloaded" });
        await page.locator(selector).waitFor();
        if (name === "markdown") {
          await page.locator(".vditor-reset, .load-fail").first().waitFor();
        }
        await page.waitForTimeout(600);
        await geometry(page, `${viewport.width}-${name}`);
        await screenshot(page, `${viewport.width}-${name}`);
      }
      await page.close();
    }
    assert.deepEqual(errors, [], `Runtime errors: ${errors.join("\n")}`);
    console.log(`UI audit passed: ${output}`);
  } finally {
    await browser.close();
  }
}
if (require.main === module) runAudit().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

module.exports = { seed, geometry, seedMessages };
