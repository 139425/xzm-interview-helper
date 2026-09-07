const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { seed, geometry } = require('./ui_audit.cjs');
const { contrast } = require('./ui_quality.cjs');
const out = path.resolve(__dirname, '../test-results/ui-navigation');
fs.mkdirSync(out, {recursive:true});
const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:5173';
const routes = [['笔面测待办','/applications/schedule','.timeline-panel'],['投递追踪','/applications','.application-row'],['秋招信息','/recruitment','.jobs-row']];
(async()=>{
 const browser = await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_BROWSER_EXECUTABLE || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 const results=[], errors=[];
 try {
  for (const width of [1440,1024,390,320]) {
   const page=await browser.newPage({viewport:{width,height:width<768?844:900}});
   await seed(page);
   const tomorrow=new Date(Date.now()+86400000).toISOString().slice(0,10);
   await page.route('**/xzm/api/schedules**',r=>r.fulfill({json:{code:200,data:{items:[{id:1,company:'星河计算',roleName:'Java 后端工程师',eventType:'INTERVIEW',startAt:tomorrow+'T14:00:00',endAt:null,notes:'准备项目深挖与数据库专题',completedAt:null},{id:2,company:'开源智造',roleName:'软件开发',eventType:'WRITTEN_TEST',startAt:tomorrow+'T19:30:00',endAt:null,notes:'算法与基础知识',completedAt:null}],summary:{},trash:[]}}}));
   page.on('pageerror',e=>errors.push(e.message));
   await page.goto(base+'/chat',{waitUntil:'networkidle'});
   const nav=page.locator(width<=768?'.mobile-workspace-nav':'.career-switcher');
   for (const [label,url,selector] of routes) {
    assert(await nav.getByRole('link',{name:label,exact:true}).isVisible(),`${width}: ${label} not directly visible`);
    const start=Date.now();
    await nav.getByRole('link',{name:label,exact:true}).click();
    await page.waitForURL('**'+url);
    await page.locator(selector).first().waitFor();
    results.push({width,page:label,readyMs:Date.now()-start});
    await page.waitForTimeout(250); // Capture settled visuals, not the route's entrance frame.
    await page.mouse.move(0,0);
    await geometry(page,`${width} ${label}`);
    await page.screenshot({path:path.join(out,`${width}-${label}.png`)});
    if(width===1440 || width===390) {
     for(const theme of ['dark','light']) {
      await page.evaluate(t=>{document.documentElement.dataset.theme=t;document.body.dataset.theme=t},theme);
      await page.waitForTimeout(250);
      assert.deepEqual(await contrast(page),[],`${theme} ${width} ${label} contrast`);
      if(theme==='dark') await page.screenshot({path:path.join(out,`${width}-${label}-dark.png`)});
     }
    }
    if(label==='笔面测待办') {
     assert.equal(await page.locator('.schedule-list > li').count(),2,'Populated schedule fixture did not render');
     const list=await page.locator('.timeline-panel').boundingBox(),entry=await page.locator('.entry-panel').boundingBox();
     assert(width>900? list.x<entry.x : list.y<entry.y,'Schedule list must precede entry form');
     await page.getByRole('button',{name:'新增安排',exact:false}).click();
     await page.waitForTimeout(350);
     assert(await page.locator('.entry-panel input[required]').first().evaluate(el=>el===document.activeElement),'New schedule did not focus company');
     await page.keyboard.press('Tab');
     await page.evaluate(()=>document.activeElement.blur());
     await page.evaluate(()=>window.scrollTo(0,0));
    }
    if(label==='秋招信息') {
     const before=await page.getByRole('combobox',{name:'招聘批次'}).isVisible();assert.equal(before,false);
     await page.getByRole('button',{name:'更多筛选',exact:false}).click();
     assert(await page.getByRole('combobox',{name:'招聘批次'}).isVisible());
     await page.getByRole('button',{name:'收起筛选',exact:false}).click();
     if(width>1250) {
      const action=await page.locator('.jobs-actions').first().boundingBox();
      assert(action.x+action.width<=width-10,'Recruitment actions clipped');
     }
    }
   }
   // Rapid switching uses the same visible navigation and ends at the requested page.
   for(const [label,url] of [...routes].reverse()) {await nav.getByRole('link',{name:label,exact:true}).click();await page.waitForURL('**'+url)}
   if(width<=768) {
    await nav.getByRole('link',{name:'AI 对话',exact:true}).click();await page.waitForURL('**/chat');
    const input=page.locator('.prompt-input textarea');await input.fill('准备下一场面试\n请围绕项目经历追问');
    await page.waitForTimeout(300);
    const composer=await page.locator('.chat-composer').boundingBox(),dock=await nav.boundingBox();
    assert(composer.y+composer.height<=dock.y,'Bottom navigation covers composer');
    await page.screenshot({path:path.join(out,`${width}-chat-composer.png`)});
    // Simulate the visual viewport available above a software keyboard.
    await page.evaluate(()=>{Object.defineProperty(visualViewport,'height',{configurable:true,value:440});visualViewport.dispatchEvent(new Event('resize'))});
    await page.waitForTimeout(100);
    assert.equal(await nav.count(),0,'Navigation must yield space to the software keyboard');
    const keyboardComposer=await page.locator('.chat-composer').boundingBox();
    assert(keyboardComposer.y+keyboardComposer.height<=441,'Composer exceeds the keyboard viewport');
    await page.evaluate(()=>{delete visualViewport.height;document.activeElement.blur();visualViewport.dispatchEvent(new Event('resize'))});
    await nav.waitFor();
    await page.emulateMedia({reducedMotion:'reduce'});await nav.getByRole('link',{name:'投递追踪',exact:true}).click();await page.waitForURL('**/applications');
   } else {
    await page.keyboard.press('Control+k');
    await page.getByRole('textbox',{name:'搜索工作区'}).waitFor({state:'visible'});
    await page.keyboard.press('Escape');
   }
   await page.close();
  }
 } finally {await browser.close()}
 fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({results,errors},null,2));
 assert.deepEqual(errors,[]);console.log(JSON.stringify({navigation:results,errors}));
})().catch(e=>{console.error(e);process.exitCode=1});
