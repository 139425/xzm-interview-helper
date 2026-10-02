'use client';

import { useId, useState } from 'react';
import { ArrowRight, ArrowUpRight, BookOpen, Check, Clock3, GraduationCap, Sparkles } from 'lucide-react';
import type { Lesson } from '@/lib/data';

export type LearningMapProps = {
  lessons: Lesson[];
  stages: string[];
  completed: string[];
  next: Lesson;
  quizCount: number;
  onOpen: (id: string) => void;
};

function JourneyArtwork({ id }: { id: string }) {
  return <svg className="lm-artwork" viewBox="0 0 360 260" fill="none" aria-hidden="true">
    <defs>
      <linearGradient id={id} x1="40" y1="220" x2="320" y2="30" gradientUnits="userSpaceOnUse">
        <stop stopColor="#a7cfee"/><stop offset="1" stopColor="#477eae"/>
      </linearGradient>
    </defs>
    {[0, 1, 2, 3, 4].map(i => <path key={i} d={`M -20 ${190 + i * 16} C 90 ${205 + i * 10}, 70 ${82 + i * 16}, 174 ${103 + i * 12} S 280 ${64 + i * 14}, 385 ${25 + i * 17}`} stroke="#91bde1" strokeOpacity={.22 + i * .04}/>) }
    <path d="M 30 212 C 122 212 56 146 154 142 S 217 68 322 56" stroke={`url(#${id})`} strokeWidth="2" strokeDasharray="3 7"/>
    {[[30, 212], [116, 154], [221, 110], [322, 56]].map(([x, y], i) => <g key={i} className={`lm-art-node lm-art-node-${i}`}>
      <circle cx={x} cy={y} r={i === 3 ? 22 : 17} fill="#edf6ff" stroke="#adcfe9"/>
      <circle cx={x} cy={y} r={i === 3 ? 8 : 5} fill={i === 3 ? '#3b78ad' : '#95beda'}/>
      <path d={`M ${x - 5} ${y + 32} H ${x + 5}`} stroke="#79a7ca" strokeWidth="2" strokeLinecap="round"/>
    </g>)}
    <path d="M 306 224 H 330 M 318 212 V 236" stroke="#739fc2" strokeOpacity=".65"/>
    <circle cx="270" cy="194" r="3" fill="#a4c8e4"/>
    <circle cx="65" cy="87" r="4" fill="#a4c8e4"/>
  </svg>;
}

export function LearningMap({ lessons, stages, completed, next, quizCount, onOpen }: LearningMapProps) {
  const [selected, setSelected] = useState<string | null>(null);
  const id = useId().replaceAll(':', '');
  const completedIds = new Set(completed);
  const learned = lessons.filter(lesson => completedIds.has(lesson.id)).length;
  const total = lessons.length;
  const percentage = total ? learned / total * 100 : 0;
  const nextIndex = Math.max(0, lessons.findIndex(lesson => lesson.id === next.id)) + 1;
  const displayedStages = selected && stages.includes(selected) ? stages.filter(stage => stage === selected) : stages;
  const progressLabel = `${learned} / ${total} 个单元已完成，${percentage.toFixed(1)}%`;

  return <div className="learning-map">
    <header className="lm-intro">
      <div>
        <span className="eyebrow">01 / LEARNING MAP</span>
        <h1>看懂股票，从这里开始。</h1>
        <p>先理解一个概念，再动手改变它。让每一次交易，都有自己的判断。</p>
      </div>
      <span className="lm-course-total"><GraduationCap size={18}/><span>A 股为主 · {total} 个单元</span></span>
    </header>

    <div className="lm-feature-grid">
      <section className="lm-feature" aria-labelledby={`${id}-continue`}>
        <div className="lm-feature-top">
          <span className="eyebrow"><span className="lm-status-dot"/>{!learned && next.id === lessons[0].id ? '你的第一站' : '继续你的学习'}</span>
          <span className="lm-folio">{String(nextIndex).padStart(2, '0')}<small> / {total}</small></span>
        </div>
        <div className="lm-feature-copy">
          <span className="lm-stage-label">{next.stage}</span>
          <h2 id={`${id}-continue`}>{next.title}</h2>
          <p>{next.coreQuestion}</p>
        </div>
        <div className="lm-feature-actions">
          <button className="button primary" onClick={() => onOpen(next.id)}>进入这一课 <ArrowUpRight size={18}/></button>
          <span><Clock3 size={16}/>{next.minutes} 分钟 · 含交互实验</span>
        </div>
        <JourneyArtwork id={`${id}-route-gradient`}/>
      </section>

      <section className="lm-progress" aria-labelledby={`${id}-progress-title`}>
        <div className="lm-progress-heading"><h2 id={`${id}-progress-title`}>每一次理解，都是积累。</h2><Sparkles size={20}/></div>
        <div className="lm-progress-body">
          <div className="lm-progress-ring" role="progressbar" aria-label="学习旅程完成进度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Number(percentage.toFixed(1))} aria-valuetext={progressLabel}>
            <svg viewBox="0 0 120 120" aria-hidden="true"><circle className="lm-ring-track" cx="60" cy="60" r="49"/><circle className="lm-ring-value" cx="60" cy="60" r="49" pathLength="100" strokeDasharray={`${percentage} 100`} transform="rotate(-90 60 60)"/></svg>
            <div><strong>{percentage.toFixed(percentage % 1 ? 1 : 0)}<small>%</small></strong><span>已完成</span></div>
          </div>
          <div className="lm-progress-details"><strong>{learned}<small> / {total}</small></strong><span>个单元已理解</span><p>{quizCount} 次知识检验</p></div>
        </div>
        <div className="lm-progress-foot"><BookOpen size={16}/><span>理解比记住更重要</span><ArrowRight size={16}/></div>
      </section>
    </div>

    <section className="lm-journey" aria-labelledby={`${id}-map-title`}>
      <div className="lm-map-heading"><div><span className="eyebrow">YOUR LEARNING JOURNEY</span><h2 id={`${id}-map-title`}>知识地图</h2><p>{stages.length} 个阶段，一条完整的思考路径。</p></div><button className={`lm-all-stages ${selected === null ? 'is-active' : ''}`} aria-pressed={selected === null} aria-controls={`${id}-courses`} onClick={() => setSelected(null)}>全部阶段 <span>{total}</span><ArrowUpRight size={16}/></button></div>
      <div className="lm-route" aria-label="按学习阶段浏览">
        {stages.map((stage, index) => {
          const group = lessons.filter(lesson => lesson.stage === stage);
          const done = group.filter(lesson => completedIds.has(lesson.id)).length;
          return <button key={stage} className={`lm-route-step ${selected === stage ? 'is-selected' : ''} ${done === group.length && group.length ? 'is-complete' : ''}`} aria-pressed={selected === stage} aria-controls={`${id}-courses`} onClick={() => setSelected(selected === stage ? null : stage)}>
            <span className="lm-route-top"><span className="lm-route-number">{String(index + 1).padStart(2, '0')}</span><span className="lm-route-count">{done} / {group.length}</span><ArrowRight size={16}/></span>
            <strong>{stage}</strong><span className="lm-route-rail"><i style={{ width: `${group.length ? done / group.length * 100 : 0}%` }}/></span>
          </button>;
        })}
      </div>
      <div className="lm-course-columns" id={`${id}-courses`} data-filtered={selected !== null}>
        {displayedStages.map(stage => {
          const stageIndex = stages.indexOf(stage);
          const group = lessons.filter(lesson => lesson.stage === stage);
          return <section className="lm-course-column" key={stage} aria-labelledby={`${id}-stage-${stageIndex}`}>
            <div className="lm-column-heading"><h3 id={`${id}-stage-${stageIndex}`}>{stage}</h3><span>{group.length} 个单元</span></div>
            <div className="lm-course-stack">{group.map(lesson => {
              const isComplete = completedIds.has(lesson.id);
              const isNext = lesson.id === next.id;
              return <button key={lesson.id} className={`lm-lesson ${isComplete ? 'is-complete' : ''} ${isNext ? 'is-next' : ''}`} onClick={() => onOpen(lesson.id)}>
                <span className="lm-lesson-top"><span className="lm-lesson-index">{String(lessons.indexOf(lesson) + 1).padStart(2, '0')}</span>{isComplete ? <span className="lm-lesson-status"><Check size={14}/> 已学习</span> : isNext ? <span className="lm-lesson-status">推荐下一课</span> : <BookOpen size={16}/>}</span>
                <h4>{lesson.title}</h4><p>{lesson.coreQuestion}</p><span className="lm-lesson-bottom"><span><Clock3 size={14}/>{lesson.minutes} 分钟</span><ArrowUpRight size={18}/></span>
              </button>;
            })}</div>
          </section>;
        })}
      </div>
      <p className="lm-map-note">可按顺序学习，也可自由探索。</p>
    </section>
  </div>;
}
