'use client';
import { useState } from 'react';
import { RotateCcw, FlaskConical, ArrowDown } from 'lucide-react';
import type { Lesson } from '@/lib/data';
import { experimentResult } from '@/lib/experiments';
import { HorizontalBars, LineGraph, PriceChart, Ring, money } from './charts';
export function Experiment({ lesson, compact = false }: { lesson: Lesson; compact?: boolean }) {
  const kind = lesson.experiment.kind, controls = lesson.experiment.controls, defaults = Object.fromEntries(controls.map(c => [c.key, c.default]));
  const [values, setValues] = useState<Record<string, number>>(defaults), r = experimentResult(kind, values);
  return <div className={`experiment ${compact ? 'compact-experiment' : ''}`}><div className="section-heading"><div><span className="eyebrow"><FlaskConical size={13}/> INTERACTIVE LAB</span><h3>{lesson.experiment.title}</h3></div><button className="icon-button" title="恢复实验默认值" aria-label="恢复实验默认值" onClick={() => setValues(defaults)}><RotateCcw size={16}/></button></div>
    <div className="experiment-layout"><div className="experiment-controls">{controls.map(c => <label className="range-control" key={c.key}><span>{c.label}<strong>{money(values[c.key], c.step < 1 ? 2 : 0)}<small>{c.unit}</small></strong></span><input type="range" min={c.min} max={c.max} step={c.step} value={values[c.key]} onChange={e => setValues(old => { const next = { ...old, [c.key]: Number(e.target.value) }; if (kind === 'candle') { next.high = Math.max(next.high, next.open, next.close); next.low = Math.min(next.low, next.open, next.close); } return next; })}/><span className="range-bounds"><small>{c.min}{c.unit}</small><small>{c.max}{c.unit}</small></span></label>)}<details className="formula"><summary>查看计算与假设</summary><p>{lesson.experiment.formula}</p><p>{lesson.experiment.assumptions}</p></details></div>
    <div className="experiment-output"><div className="experiment-metrics">{r.metrics.map(m => <div key={m.label}><span>{m.label}</span><strong className={m.value < 0 ? 'down' : ''}>{Number.isFinite(m.value) ? money(m.value, m.digits) : '不适用'}<small>{Number.isFinite(m.value) && m.unit}</small></strong></div>)}</div>
      {kind === 'equity' && <div className="ownership-visual"><div className="ownership-grid" role="img" aria-label={`100格代表整家公司，你持有${values.shares / 100}%`}>{Array.from({ length: 100 }, (_, i) => <i key={i} className={i < values.shares / 100 ? 'owned' : ''}/>)}</div><div><Ring value={values.shares / 100} label="你的份额"/><small>每一格 = 1% 股权</small></div></div>}
      {kind === 'candle' && <PriceChart candles={[{ day: 0, date: '前一日', open: 10, close: 10, high: 10.2, low: 9.8, volume: 18000 }, { day: 1, date: '实验日', open: values.open, close: values.close, high: Math.max(values.high, values.open, values.close), low: Math.min(values.low, values.open, values.close), volume: values.volume }]}/>}
      {r.series && <LineGraph series={r.series} labels={r.labels} unit={r.chartUnit} height={230}/>}
      {r.bars && <HorizontalBars items={r.bars} unit={r.chartUnit}/>}
      <div className="observation"><ArrowDown size={17}/><p>{r.message}</p></div>
    </div></div></div>;
}
