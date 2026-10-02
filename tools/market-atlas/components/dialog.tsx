'use client';
import { useEffect, useRef, useContext, createContext, type ReactNode } from 'react';
import { X } from 'lucide-react';
export const DialogErrorContext = createContext('');
export function Dialog({ title, children, onClose, wide = false }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  const error = useContext(DialogErrorContext);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const node = ref.current; node?.showModal(); const previous = document.activeElement as HTMLElement; return () => { node?.close(); previous?.focus(); }; }, []);
  return <dialog ref={ref} className={`dialog ${wide ? 'wide-dialog' : ''}`} onCancel={onClose} onClick={e => { if (e.target === e.currentTarget) { const rect = e.currentTarget.getBoundingClientRect(); if (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom) onClose(); } }} aria-labelledby="dialog-title"><div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button className="icon-button" aria-label="关闭弹窗" onClick={onClose}><X size={20}/></button></div><div className="dialog-content">{error && <p className="soft-alert" role="alert">{error}</p>}{children}</div></dialog>;
}
