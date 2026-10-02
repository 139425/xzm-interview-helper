'use client';

import { useState } from 'react';
import { LineGraph, money } from './charts';

export function DailyAverageExample() {
  const [previous, setPrevious] = useState(450000);
  const [last, setLast] = useState(1000000);
  const average = (previous * 19 + last) / 20;
  const labels = Array.from({ length: 20 }, (_, i) => `交易日 ${i + 1}`);
  return <section className="average-example" aria-label="20个交易日日均资产实验">
    <h3>最后一天转入很多，日均就达标了吗？</h3>
    <p>先把前 19 个交易日的平均值看作一组，再加入第 20 日。这里只演算算术平均，用 50 万元作为比较线。</p>
    <div className="average-controls">
      <label><span>前 19 个交易日平均资产 <b>¥ {money(previous, 0)}</b></span><input type="range" min="0" max="1000000" step="10000" value={previous} onChange={e => setPrevious(Number(e.target.value))}/></label>
      <label><span>第 20 个交易日资产 <b>¥ {money(last, 0)}</b></span><input type="range" min="0" max="2000000" step="10000" value={last} onChange={e => setLast(Number(e.target.value))}/></label>
    </div>
    <div className="average-result" role="status" aria-live="polite"><span>20 日平均 =（{money(previous, 0)} × 19 + {money(last, 0)}）÷ 20</span><strong>¥ {money(average, 0)}</strong><b>{average >= 500000 ? '平均值达到 50 万元这条比较线' : '平均值仍低于 50 万元这条比较线'}</b></div>
    <LineGraph series={[{name:'每天资产',values:Array.from({length:20},(_,i)=>i===19?last:previous),color:'#427db4'},{name:'20日平均',values:Array(20).fill(average),color:'#466c8c',dashed:true},{name:'50万元比较线',values:Array(20).fill(500000),color:'#785697',dashed:true}]} labels={labels} height={230} unit="元"/>
    <p className="data-caption">前 19 日曲线用它们的平均值简化显示，真实每日金额可以不同。资产计算范围、经验、权限例外和审核仍按对应板块规则核对；平均值达到比较线只说明这个算式的结果。</p>
  </section>;
}
