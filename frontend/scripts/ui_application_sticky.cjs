// Scroll a populated table: empty/short fixtures cannot expose sticky gaps.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { seed, geometry } = require('./ui_audit.cjs');
const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:5173';
const output = path.resolve(__dirname, '../test-results/application-sticky');
fs.mkdirSync(output, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  const report = [], errors = [];
  try {
    for (const width of [1680, 1440, 1024, 390, 320]) {
      const page = await browser.newPage({ viewport: { width, height: 940 } });
      await seed(page);
      await page.route('**/xzm/api/applications**', route => route.fulfill({ json: { code: 200, data: {
        items: Array.from({ length: 142 }, (_, i) => ({ id: i + 1, company: `测试公司 ${i + 1}`, status: 'INTERVIEW_2', applyUrl: 'https://example.com', roleName: '后端开发', updatedAt: '2026-09-07' })), summary: { INTERVIEW_2: 142 },
      } } }));
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(base + '/applications', { waitUntil: 'networkidle' });
      assert.equal(await page.locator('.application-row').count(), 142);
      await geometry(page, `applications ${width}`);
      const initial = await page.locator('.pipeline-overview').boundingBox();
      const topbar = await page.locator('.workspace-frame__topbar').boundingBox();
      assert.equal(Math.round(initial.y - topbar.y - topbar.height), width > 768 ? 30 : 22, 'Initial overview spacing changed');
      if (width > 768) {
        for (const collapsed of [false, true]) {
          if (collapsed) { await page.getByTitle('收起侧边栏').click(); await page.waitForTimeout(250); }
          for (const scrollTop of [400, 1400, 600]) {
            await page.locator('.application-main').evaluate((el, top) => { el.scrollTop = top; }, scrollTop);
            await page.waitForTimeout(100);
            const header = await page.locator('.workspace-frame__topbar').boundingBox();
            const toolbar = await page.locator('.sheet-toolbar').boundingBox();
            const columns = await page.locator('.application-table th').first().boundingBox();
            const gap = toolbar.y - header.y - header.height;
            assert(Math.abs(gap) < 0.5, `${width} collapsed=${collapsed}: sticky gap ${gap}px`);
            assert(Math.abs(columns.y - toolbar.y - toolbar.height) < 0.5, 'Table headings separated from toolbar');
            report.push({ width, collapsed, scrollTop, gap });
          }
          // Showing the reset button can wrap the filters; headings must follow
          // the toolbar's measured height after that change too.
          await page.getByPlaceholder('搜索公司、岗位或备注').fill('测试公司');
          await page.waitForTimeout(150);
          const toolbar = await page.locator('.sheet-toolbar').boundingBox();
          const columns = await page.locator('.application-table th').first().boundingBox();
          assert(Math.abs(columns.y - toolbar.y - toolbar.height) < 0.5, 'Resized filters cover table headings');
          await page.getByPlaceholder('搜索公司、岗位或备注').fill('');
          await page.screenshot({ path: path.join(output, `${width}-${collapsed ? 'collapsed' : 'expanded'}.png`) });
        }
      } else {
        assert.equal(await page.locator('.sheet-toolbar').evaluate(el => getComputedStyle(el).position), 'static');
        await page.screenshot({ path: path.join(output, `${width}-mobile.png`) });
      }
      await page.close();
    }
  } finally { await browser.close(); }
  assert.deepEqual(errors, []);
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify({ report, errors }, null, 2));
  console.log(JSON.stringify({ scrollChecks: report.length, maxGap: Math.max(...report.map(r => Math.abs(r.gap))), errors }));
})().catch(error => { console.error(error); process.exitCode = 1; });
