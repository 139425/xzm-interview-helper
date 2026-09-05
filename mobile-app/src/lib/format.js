import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

export function markdown(value) {
  return DOMPurify.sanitize(marked.parse(String(value || '')), {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'form'],
    FORBID_ATTR: ['style'],
  })
}

export function dateLabel(value, includeTime = false) {
  if (!value) return '待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', includeTime
    ? { month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false }
    : { month: 'numeric', day: 'numeric' }).format(date)
}

export function relativeTime(value) {
  const time = new Date(value).getTime()
  if (!Number.isFinite(time)) return ''
  const diff = time - Date.now()
  const days = Math.round(diff / 86_400_000)
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  if (days === -1) return '昨天'
  if (days > 1 && days < 8) return `${days} 天后`
  if (days < -1 && days > -8) return `已过 ${Math.abs(days)} 天`
  return dateLabel(value)
}

export function initials(value, fallback = 'X') {
  return String(value || fallback).replace(/[^\p{L}\p{N}]/gu, '').slice(0, 1).toUpperCase() || fallback
}
