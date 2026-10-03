'use client';
import { useEffect, useRef, useState } from 'react';
import { ArrowRight, ArrowUpRight, BookOpen, ExternalLink, MessageCircle, Plus, Send, Square, X } from 'lucide-react';
import type { TutorAnswer, TutorMessage } from '@/lib/tutor';
import { chapterById } from '@/lib/course';
type Turn = {question:string; result?:TutorAnswer};
export function StudyAssistant({open,onClose,initialQuestion,lessonId,completed,onOpenLesson}: {open:boolean;onClose:()=>void;initialQuestion:{text:string;request:number};lessonId:string|null;completed:string[];onOpenLesson:(id:string,block?:string)=>void}) {
  const [seed,setSeed] = useState(initialQuestion.request);
  const [question,setQuestion] = useState(initialQuestion.text), [turns,setTurns] = useState<Turn[]>([]), [busy,setBusy] = useState(false), [error,setError] = useState('');
  const dialog = useRef<HTMLDialogElement>(null), input = useRef<HTMLTextAreaElement>(null), bottom = useRef<HTMLDivElement>(null), controller = useRef<AbortController|null>(null), lastFocus = useRef<HTMLElement|null>(null), sending = useRef(false);
  useEffect(() => { if (open) { lastFocus.current = document.activeElement as HTMLElement; dialog.current?.showModal(); input.current?.focus(); } else { dialog.current?.close(); lastFocus.current?.focus(); } },[open]);
  if (seed !== initialQuestion.request) { setSeed(initialQuestion.request); setQuestion(initialQuestion.text); }
  useEffect(() => { if (open) bottom.current?.scrollIntoView({behavior:'smooth',block:'nearest'}); },[turns,busy,open]);
  useEffect(() => () => controller.current?.abort(),[]);
  async function send(value=question) {
    if (sending.current || !value.trim()) return;
    sending.current=true; setBusy(true); setError('');
    const pending=value.trim(); setQuestion(''); setTurns(old=>[...old,{question:pending}]);
    controller.current=new AbortController();
    const history:TutorMessage[]=turns.filter(t=>t.result).slice(-3).flatMap(t=>[{role:'user' as const,content:t.question},{role:'assistant' as const,content:t.result!.answer.slice(0,4000)}]);
    try {
      const response=await fetch('/tools/market-atlas/api/tutor',{method:'POST',headers:{'Content-Type':'application/json'},signal:controller.current.signal,body:JSON.stringify({question:pending,lessonId,completed,history})});
      const result=await response.json() as TutorAnswer & {error?:string};
      if (!response.ok || !result.answer) throw new Error(result.error || '暂时没有收到回答，请重试。');
      setTurns(old=>old.map((turn,i)=>i===old.length-1?{...turn,result}:turn));
    } catch(e) { setError((e as Error).name==='AbortError'?'已停止生成，问题可以修改后重发。':(e as Error).message); setQuestion(pending); setTurns(old=>old.filter((_,i)=>i!==old.length-1)); }
    finally { sending.current=false;setBusy(false);controller.current=null; }
  }
  return <dialog ref={dialog} className="tutor-dialog" aria-labelledby="tutor-title" onCancel={onClose} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose();}}}>
    <header className="tutor-header"><div className="tutor-symbol"><MessageCircle size={22}/></div><div><h2 id="tutor-title">学习助手</h2><p>DeepSeek 4.1 · 非思考</p></div><button className="icon-button" aria-label="开始新对话" disabled={busy} onClick={()=>{setTurns([]);setError('');setQuestion('');input.current?.focus();}}><Plus size={20}/></button><button className="icon-button" aria-label="关闭学习助手" onClick={onClose}><X size={20}/></button></header>
    <div className="tutor-context"><BookOpen size={14}/>{lessonId?`一起读：${chapterById(lessonId)?.title}`:'结合这套课程，回答你的学习问题'}</div>
    <div className="tutor-messages" aria-live="polite" aria-relevant="additions text">
      {!turns.length && <div className="tutor-welcome"><span className="reading-kicker">不用把问题组织得很专业</span><h3>哪一句没懂，<br/>就从哪一句聊起。</h3><p>我会换个例子讲，也会告诉你接着读哪一节、去哪里核对。</p>{['股票和存款到底有什么不同？','我不懂买一卖一，能用买东西的例子讲吗？','读完开户流程，接下来应该学什么？'].map(q=><button key={q} onClick={()=>void send(q)} disabled={busy}>{q}<ArrowUpRight size={15}/></button>)}<small>回答基于课程与已核验来源，不提供实时行情。</small></div>}
      {turns.map((turn,i)=><section key={i} className="tutor-turn"><div className="tutor-question">{turn.question}</div>{turn.result && <div className="tutor-answer"><div className="tutor-answer-text">{turn.result.answer.split(/\n+/).filter(Boolean).map((p,j)=><p key={j}>{p}</p>)}</div>{turn.result.lessons.length>0&&<div className="tutor-lessons"><span>读到这里，会更清楚</span>{turn.result.lessons.map(lesson=><a key={lesson.id} href={lesson.href} onClick={e=>{e.preventDefault();onOpenLesson(lesson.id,lesson.blockId);}}><BookOpen size={17}/><div><b>{lesson.title}</b>{lesson.section&&<small>{lesson.section}</small>}<p>{lesson.reason}</p></div><ArrowRight size={15}/></a>)}</div>}{turn.result.sources.length>0&&<details className="tutor-sources"><summary>核对原始资料 · {turn.result.sources.length}条</summary>{turn.result.sources.map(source=><a href={source.url} key={source.id} target="_blank" rel="noreferrer">{source.title}<small>资料核验 {source.asOf}</small><ExternalLink size={13}/></a>)}</details>}{i===turns.length-1&&turn.result.followUps.length>0&&<div className="tutor-followups">{turn.result.followUps.map(q=><button key={q} disabled={busy} onClick={()=>void send(q)}>{q}<ArrowUpRight size={13}/></button>)}</div>}</div>}</section>)}
      {busy&&<div className="tutor-pending" role="status"><i/><i/><i/><span>正在查课程，组织一个具体的解释…</span></div>}{error&&<p className="tutor-error" role="alert">{error}</p>}<div ref={bottom}/>
    </div>
    <form className="tutor-compose" onSubmit={e=>{e.preventDefault();void send();}}><label className="sr-only" htmlFor="tutor-question">你的学习问题</label><textarea ref={input} id="tutor-question" value={question} maxLength={1200} rows={3} placeholder="例如：为什么公司赚钱，我的股票却跌了？" onChange={e=>setQuestion(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'&&(e.metaKey||e.ctrlKey)){e.preventDefault();void send();}}}/><div><small>只发送本次对话和课程上下文，请勿填写账户密码或证件信息。</small>{busy?<button type="button" aria-label="停止生成" onClick={()=>controller.current?.abort()}><Square size={16}/></button>:<button type="submit" disabled={!question.trim()} aria-label="发送问题"><Send size={18}/></button>}</div></form>
  </dialog>;
}
