'use client';
import { useEffect, useState } from 'react';
import { ArrowRight, Check, ChevronDown, MessageCircle, RotateCcw } from 'lucide-react';
import { chapterById, type ReadingBlock } from '@/lib/course';
import type { Lesson } from '@/lib/data';
import { Experiment } from './experiment';
import { ConceptFigure } from './concept-figures';
import { DailyAverageExample } from './daily-average';
import './lesson-reader.css';

function Ownership() {
  const [shares, setShares] = useState(100);
  return <div className="ownership-demo"><div className="ownership-grid" role="img" aria-label={`一千份中的${shares}份，占${shares / 10}%`}>{Array.from({ length: 100 }, (_, i) => <i key={i} className={i < shares / 10 ? 'owned' : ''}/>)}</div><div><label htmlFor="ownership-range">你持有 <strong>{shares} 份</strong> / 全部1,000份</label><input id="ownership-range" type="range" min="10" max="1000" step="10" value={shares} onChange={e => setShares(Number(e.target.value))}/><output aria-live="polite"><b>{shares / 10}%</b> 的公司所有权</output><p>一个方格代表10份。拿到多少份，要除以全部份数，才是你的比例。</p></div></div>;
}
function FirstOrder() {
  const [offer, setOffer] = useState('9.98'), [supply, setSupply] = useState('60');
  const filled = Number(offer) <= 10 ? Math.min(100, Number(supply)) : 0;
  return <div className="order-walkthrough"><p>你的限价买单：<b>100股，最高10.00元</b></p><div><label>当前最低卖价<select value={offer} onChange={e => setOffer(e.target.value)}><option>9.98</option><option>10.00</option><option>10.02</option></select></label><label>该价格可卖股数<select value={supply} onChange={e => setSupply(e.target.value)}><option>60</option><option>100</option><option>200</option></select></label></div><output aria-live="polite"><b>{filled === 0 ? '暂未成交' : filled === 100 ? '全部成交' : '部分成交'}</b><span>成交 {filled} 股 · 剩余 {100 - filled} 股</span><p>{filled ? `按卖价${offer}元成交，成交金额${(filled * Number(offer)).toFixed(2)}元，另计费用。` : '卖家要价超过你的最高买价，系统不会擅自加价。'}</p></output><small>教学快照：忽略其他订单排队与报价变化。</small></div>;
}
function Checkpoint({ block }: { block: ReadingBlock }) {
  const [selected, setSelected] = useState<number | null>(null);
  return <div className="reading-check"><span className="reading-kicker">停一下，自己判断</span><h2>{block.title}</h2><p>{block.question}</p><div className="reading-options">{block.options?.map((option, index) => <button key={option} type="button" className={selected === index ? index === block.answer ? 'is-correct' : 'is-incorrect' : ''} onClick={() => setSelected(index)} aria-pressed={selected === index}><span>{String.fromCharCode(65 + index)}</span>{option}{selected === index && index === block.answer && <Check size={18}/>}</button>)}</div>{selected !== null && <div className="reading-feedback" role="status"><b>{selected === block.answer ? '这一步理解了。' : '看看漏掉了什么。'}</b> {block.explanations?.[selected]}<button className="text-button" onClick={() => setSelected(null)}><RotateCcw size={13}/>再想一次</button></div>}</div>;
}
export function DetailedLessonBody({ lesson, ask }: { lesson: Lesson; ask: (question: string) => void }) {
  const chapter = chapterById(lesson.id)!;
  const [active, setActive] = useState(chapter.blocks[0].id);
  const [checked, setChecked] = useState<string[]>([]);
  useEffect(() => {
    const observer = new IntersectionObserver(entries => { for (const entry of entries) if (entry.isIntersecting) setActive(entry.target.id); }, { rootMargin: '-12% 0px -65% 0px' });
    document.querySelectorAll('.reading-block').forEach(el => observer.observe(el));
    return () => observer.disconnect();
  }, [lesson.id]);
  return <div className="reading-layout"><div className="reading-article">{chapter.blocks.map(block => <section key={block.id} id={block.id} className={`reading-block block-${block.type}`} tabIndex={-1}>
    {block.type === 'check' ? <Checkpoint block={block}/> : <>
      {block.type === 'story' && <span className="reading-kicker">把这件事放进生活里</span>}
      <h2>{block.title}</h2>
      {block.paragraphs?.map((p, i) => <p key={i}>{p}</p>)}
      {block.type === 'table' && <div className="reading-table" tabIndex={0} role="region" aria-label={block.title}><table><thead><tr>{block.columns?.map(c => <th key={c} scope="col">{c}</th>)}</tr></thead><tbody>{block.rows?.map((row, i) => <tr key={i}>{row.map((cell, j) => j === 0 ? <th key={j} scope="row">{cell}</th> : <td key={j}>{cell}</td>)}</tr>)}</tbody></table></div>}
      {block.type === 'steps' && <ol className="reading-steps">{block.items?.map((item, i) => <li key={item}><span>{i + 1}</span><p>{item}</p></li>)}</ol>}
      {block.type === 'task' && <div className="reading-tasks"><p className="reading-task-note">给自己的练习，不计分；勾选仅用于本次阅读。</p>{block.items?.map((item, i) => <label key={item}><input type="checkbox" checked={checked.includes(`${block.id}-${i}`)} onChange={e => setChecked(old => e.target.checked ? [...old, `${block.id}-${i}`] : old.filter(v => v !== `${block.id}-${i}`))}/><span>{item}</span></label>)}</div>}
      {block.type === 'figure' && (block.kind === 'ownership' ? <Ownership/> : block.kind === 'average' ? <DailyAverageExample/> : lesson.id === 'beginner-first-order' ? <FirstOrder/> : <ConceptFigure id={block.kind === 'accounts' ? 'beginner-accounts' : block.kind!}/>)}
      {block.type === 'experiment' && <div className="reading-experiment"><Experiment lesson={lesson}/></div>}
      {block.after && <p className="reading-caption">{block.after}</p>}
    </>}
    <button className="block-ask" onClick={() => ask(`我正在读《${chapter.title}》的“${block.title}”，请换个更简单的例子解释这一段。`)}><MessageCircle size={14}/>这段没懂，问一下</button>
  </section>)}<div className="reading-bridge"><span>下一步为什么学这个</span><p>{chapter.nextWhy}</p><ArrowRight size={20}/></div></div>
  <aside className="reading-toc"><details open><summary>这一课怎么读<ChevronDown size={16}/></summary><nav aria-label="本课目录">{chapter.blocks.map((block, i) => <a key={block.id} href={`#${block.id}`} className={active === block.id ? 'current' : ''} aria-current={active === block.id ? 'location' : undefined}><span>{String(i + 1).padStart(2, '0')}</span>{block.title}</a>)}</nav></details><div className="reading-toc-help"><MessageCircle size={21}/><b>卡住很正常。</b><p>告诉助手你哪句话没懂，它会从这一课接着解释。</p><button onClick={() => ask(`我正在学《${chapter.title}》，请帮我检查需要先懂什么。`)}>陪我理一理 <ArrowRight size={14}/></button></div></aside></div>;
}
