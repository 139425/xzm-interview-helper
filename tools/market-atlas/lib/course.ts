import content from './course-content.json' with { type: 'json' };
export type ReadingBlock = {
  id: string; type: 'text' | 'story' | 'table' | 'check' | 'steps' | 'figure' | 'task' | 'experiment'; title: string;
  paragraphs?: string[]; columns?: string[]; rows?: string[][]; after?: string; items?: string[];
  question?: string; options?: string[]; answer?: number; explanations?: string[]; kind?: string;
};
export type Chapter = { id: string; title: string; subtitle: string; outcome: string; minutes: number; format: string; prerequisites: string[]; nextWhy: string; blocks: ReadingBlock[] };
export const CHAPTERS = content.lessons as Chapter[];
export const COURSE_VERSION = content.version;
export const chapterById = (id: string) => CHAPTERS.find(chapter => chapter.id === id);
export const chapterHref = (id: string, block?: string) => `/tools/market-atlas/?lesson=${encodeURIComponent(id)}${block ? `#${encodeURIComponent(block)}` : ''}`;
export function blockText(block: ReadingBlock): string {
  return [block.title, ...(block.paragraphs || []), ...(block.items || []), ...(block.rows || []).map(row => row.join('：')), block.after || '', block.question || '', ...(block.explanations || [])].join('\n');
}
