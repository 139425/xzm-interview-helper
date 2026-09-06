const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { seed, geometry } = require('./ui_audit.cjs');
const out = path.resolve(__dirname, '../test-results/ui-quality');
fs.mkdirSync(out, { recursive: true });

async function contrast(page) {
  return page.evaluate(() => {
    const rgb = value => { const m = value.match(/[-\d.]+/g); if (!m) return [0,0,0,0]; const scale = value.startsWith('color(srgb') ? 255 : 1; return [...m.slice(0,3).map(n => +n * scale), +(m[3] ?? 1)]; };
    const mix = (fg, bg) => fg.slice(0, 3).map((n, i) => n * fg[3] + bg[i] * (1 - fg[3])).concat(1);
    const luminance = c => c.slice(0, 3).map(x => x / 255).map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4).reduce((s, n, i) => s + n * [.2126, .7152, .0722][i], 0);
    const failures = [], seen = new Set();
    for (const el of document.querySelectorAll('body *')) {
      if (!el.getClientRects().length || el.closest('[aria-hidden="true"], [inert], [disabled], .el-button.is-disabled, .sr-only, .xzm-sr-only, .visually-hidden')) continue;
      const style = getComputedStyle(el);
      if (style.visibility !== 'visible' || +style.opacity === 0 || el.namespaceURI !== 'http://www.w3.org/1999/xhtml') continue;
      const text = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent).join('').trim();
      if (!text || !/[\p{L}\p{N}]/u.test(text)) continue;
      const ancestors = []; let node = el;
      while (node) { ancestors.unshift(getComputedStyle(node)); node = node.parentElement; }
      let bg = [255, 255, 255, 1], opacity = 1;
      for (const cs of ancestors) {
        bg = mix(rgb(cs.backgroundColor), bg); opacity *= +cs.opacity;
        const gradient = cs.backgroundImage.match(/rgba?\([^)]+\)/);
        if (gradient && cs.backgroundClip !== 'text') bg = mix(rgb(gradient[0]), bg);
      }
      if (opacity < .01) continue;
      const color = rgb(style.color); color[3] *= opacity;
      const fg = mix(color, bg), a = luminance(fg), b = luminance(bg), ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
      const threshold = parseFloat(style.fontSize) >= 24 || (parseFloat(style.fontSize) >= 18.67 && +style.fontWeight >= 700) ? 3 : 4.5;
      if (ratio >= threshold - .05) continue;
      const signature = `${style.color}|${bg.join(',')}|${el.className}`;
      if (seen.has(signature)) continue; seen.add(signature);
      failures.push({ text: text.slice(0, 45), class: el.className, ratio: +ratio.toFixed(2), color: style.color, background: bg.slice(0,3).map(Math.round), opacity: +opacity.toFixed(2) });
    }
    return failures.sort((a, b) => a.ratio - b.ratio);
  });
}

async function run() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:5173';
  const report = [], errors = [];
  try {
    for (const theme of ['light', 'dark']) {
      const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
      await seed(page);
      await page.addInitScript(t => { localStorage.setItem('theme', t); localStorage.setItem('xzm-theme', t); }, theme);
      page.on('pageerror', e => errors.push(e.message));
      for (const [name, route] of [['chat','/chat'], ['interview','/aiInterview'], ['algorithm','/algorithms'], ['recruitment','/recruitment'], ['applications','/applications'], ['schedule','/applications/schedule'], ['knowledge','/knowledge'], ['server','/admin/server'], ['users','/admin/users'], ['report','/interview-report'], ['login','/login'], ['register','/register']]) {
        await page.goto(base + route, { waitUntil: 'networkidle' });
        await geometry(page, theme + '-' + name);
        const failures = await contrast(page);
        report.push({ theme, name, failures });
        await page.screenshot({ path: path.join(out, `${theme}-${name}.png`), fullPage: true });
        console.log(`${theme} ${name}: ${failures.length} contrast findings`);
      }
      await page.goto(base + '/chat', { waitUntil: 'networkidle' });
      const side = await page.locator('.gemini-sidebar').boundingBox();
      const nav = await page.locator('.workspace-switcher').boundingBox();
      const history = await page.locator('.history-list').boundingBox();
      assert(nav.height < 180, `Navigation too tall: ${nav.height}`);
      assert(history.height > side.height * .55, `History squeezed: ${history.height}/${side.height}`);
      console.log(`${theme} sidebar: navigation ${nav.height}px, history ${history.height}px / ${side.height}px`);
      const list = page.locator('.history-list');
      await list.evaluate(el => el.scrollTop = 240);
      await page.getByTitle('收起侧边栏').click();
      await page.getByTitle('展开侧边栏').click();
      assert.equal(await list.evaluate(el => el.scrollTop), 240, 'Collapse reset history position');
      await page.getByRole('button', { name: '全部工作区', exact: true }).click();
      const search = page.getByRole('textbox', { name: '搜索工作区' });
      await search.focus();
      await page.keyboard.press('ArrowUp');
      assert(await page.locator('.workspace-option').last().evaluate(el => el === document.activeElement), 'ArrowUp from search must select last item');
      await page.keyboard.press('Tab');
      assert(await search.evaluate(el => el === document.activeElement), 'Tab must return to search');
      await search.fill('不存在的工作区');
      assert(await page.getByText('没有匹配的工作区').isVisible());
      await page.keyboard.press('Tab');
      assert(await search.evaluate(el => el === document.activeElement), 'Empty search lost focus');
      await search.fill('');
      report.push({ theme, name: 'workspace-picker', failures: await contrast(page) });
      await page.screenshot({ path: path.join(out, `${theme}-workspace-picker.png`) });
      await page.getByRole('textbox', { name: '搜索工作区' }).fill('资料');
      assert.equal(await page.locator('.workspace-option:visible').count(), 1);
      await page.getByRole('button', { name: '个人资料', exact: true }).click();
      await page.waitForURL('**/knowledge');
      assert(await page.locator('.mode-btn.active').textContent().then(t => t.includes('个人资料')));
      await page.setViewportSize({ width: 320, height: 720 });
      await page.waitForTimeout(250);
      await page.getByRole('button', { name: '展开侧边栏', exact: true }).last().click();
      await page.screenshot({ path: path.join(out, `${theme}-mobile-sidebar.png`) });
      await geometry(page, theme + '-mobile');
      await page.getByRole('button', { name: '全部工作区', exact: true }).click();
      await page.getByRole('textbox', { name: '搜索工作区' }).focus();
      await geometry(page, theme + '-mobile-picker');
      await page.keyboard.press('Escape');
      await page.locator('.workspace-picker').waitFor({ state: 'hidden' });
      assert(await page.locator('.gemini-sidebar').evaluate(el => el.classList.contains('expanded')), 'Picker Escape closed sidebar too');
      await page.keyboard.press('Escape');
      assert(await page.locator('.gemini-sidebar').evaluate(el => !el.classList.contains('expanded')), 'Second Escape did not close sidebar');
      await page.close();
    }
  } finally { await browser.close(); }
  fs.writeFileSync(path.join(out, 'contrast.json'), JSON.stringify({ report, errors }, null, 2));
  assert.deepEqual(errors, []);
  console.log(`Quality report: ${out}`);
  if (process.env.STRICT_CONTRAST === '1') assert.equal(report.reduce((n, r) => n + r.failures.length, 0), 0, 'Text contrast findings remain');
}
run().catch(e => { console.error(e); process.exitCode = 1; });
