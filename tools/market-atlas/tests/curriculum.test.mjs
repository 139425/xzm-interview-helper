import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
const read=p=>JSON.parse(readFileSync(new URL(p,import.meta.url),'utf8'));
const curriculum=read('../lib/curriculum.json'),course=read('../lib/course-content.json');
const ids=course.lessons.map(l=>l.id),lessons=new Map(curriculum.lessons.map(l=>[l.id,l]));
test('course routes and stage order agree, with every prerequisite taught earlier',()=>{
 assert.equal(curriculum.schemaVersion,3); assert.equal(course.version,3);
 assert.equal(new Set(ids).size,21); assert.deepEqual(curriculum.stages.flatMap(s=>s.ids),ids);
 assert.deepEqual([...lessons.keys()],ids); assert.equal(ids[0],'equity');
 for(const [i,c] of course.lessons.entries()) for(const id of c.prerequisites) assert.ok(ids.indexOf(id)>=0&&ids.indexOf(id)<i,`${c.id} requires ${id}`);
});
test('every visible section has a unique deep link and valid content for its actual teaching method',()=>{
 for(const c of course.lessons){
  assert.equal(new Set(c.blocks.map(b=>b.id)).size,c.blocks.length,c.id);
  assert.ok(c.outcome&&c.nextWhy&&c.format);
  for(const b of c.blocks){
   assert.match(b.id,/^[a-z][a-z0-9-]*$/); assert.ok(b.title);
   if(['text','story'].includes(b.type))assert.ok(b.paragraphs?.every(p=>p.trim()));
   if(['steps','task'].includes(b.type))assert.ok(b.items?.every(p=>p.trim()));
   if(b.type==='table')assert.ok(b.rows?.every(row=>row.length===b.columns.length));
   if(b.type==='check'){assert.ok(b.options[b.answer]);assert.equal(b.explanations.length,b.options.length);}
  }
 }
 // No word or paragraph quota: distinct structures reflect different lesson needs.
 assert.ok(new Set(course.lessons.map(c=>c.blocks.map(b=>b.type).join(','))).size>10);
 assert.ok(curriculum.lessons.every(l=>!l.guide));
});
test('new exercises have independent identities, bounded answers and meaningful explanations',()=>{
 for(const l of lessons.values()){
  assert.equal(l.quizRevision,3); assert.equal(new Set(l.quizzes.map(q=>q.question)).size,l.quizzes.length);
  for(const q of l.quizzes){assert.ok(Number.isInteger(q.answerIndex)&&q.options[q.answerIndex]);assert.ok(q.explanation);assert.equal(new Set(q.options).size,q.options.length);}
 }
});
test('sources are dated and scoped primary HTTPS documents, not invented model links',()=>{
 const official=['csrc.gov.cn','sac.net.cn','sse.com.cn','szse.cn','bse.cn','chinaclear.cn','mof.gov.cn','chinatax.gov.cn','spp.gov.cn','sec.gov','investor.gov','finra.org'];
 for(const l of lessons.values())for(const s of l.sources){
  const u=new URL(s.url);assert.equal(u.protocol,'https:');assert.ok(official.some(d=>u.hostname===d||u.hostname.endsWith('.'+d)),s.url);
  const n=l.sourceNotes.find(n=>n.url===s.url);assert.ok(n?.supports&&n.jurisdiction,s.url);assert.match(n.asOf,/^2026-10-0[23]$/);
 }
});
function answer(id,index=0){const q=lessons.get(id).quizzes[index];return q.options[q.answerIndex];}
test('independent arithmetic checks the teaching examples and transfer questions',()=>{
 assert.equal(answer('equity'),`${100/10000*100}%`);
 assert.equal(answer('compound'),`${1080+20-10-1000}元`);
 assert.equal(answer('compound',1),`${Math.round(100*1.1**2)}元`);
 assert.equal(answer('drawdown'),`${(100/80-1)*100}%`);
 assert.equal(answer('drawdown',1),`${(120-90)/120*100}%`);
 assert.equal(answer('beginner-markets'),`${(19*0+50)/20}万元`);
 assert.equal(answer('position'),`${10*.8*.25}万元`);
 const fees=5+5+1010*.0005+(1000+1010)*.00001;
 assert.equal((1010-1000-fees).toFixed(4),'-0.5251');
 assert.match(JSON.stringify(course.lessons.find(l=>l.id==='costs')),/0\.5251/);
});
test('critical misconceptions are explained in the course instead of hidden in footnotes',()=>{
 const text=id=>JSON.stringify(course.lessons.find(l=>l.id===id));
 assert.match(text('equity'),/不等于立刻全分/);assert.match(text('equity'),/卖方/);
 assert.match(text('beginner-first-order'),/周末不算/);assert.match(text('beginner-first-order'),/撤单不会撤回/);
 assert.match(text('beginner-opening'),/16/);assert.match(text('beginner-markets'),/科创板/);
 assert.match(text('candle'),/9\.5/); assert.match(text('dividend'),/不.*保证|不.*固定/);
 assert.match(text('etf'),/当日|当天/);assert.match(text('beginner-protection'),/独立/);
});
