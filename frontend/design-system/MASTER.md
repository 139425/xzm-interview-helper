# XZM Interview Helper — Porcelain / Iris — V6

界面围绕输入、阅读、练习和求职记录组织。所有既有业务能力保留，视觉层采用瓷白画布、石墨文字与淡鸢尾蓝强调色。

## Theme

- Canvas `#F8F8FA`; sidebar `#F0F0F3`; paper `#FFFFFF`.
- Text `#25262C`; secondary `#5B5D6A`; muted `#656775`.
- Accent `#515E9F`; active surface `#EEEFF8`.
- Dark canvas `#18191E`; dark paper `#25262E`; accent `#B9C1EF`.
- Use `tokens.css` and `theme.css` semantic variables. Algorithm surfaces bridge to these same tokens; Monaco's editor theme is matched separately.
- Success, warning and danger are semantic states, not decorative brand colors.

## Layout

- Desktop sidebar: 224px / 64px. Training occupies a compact three-tab row. 笔面测待办、投递追踪、秋招信息 each have a permanent labeled link. The selected extra workspace stays visible. A searchable picker (Cmd/Ctrl+K) exposes every permitted workspace. History and problem catalog use the remaining height and preserve their state when the rail folds.
- Main headers: calm, consistent hierarchy. Chat reading width: at most 800px.
- Chat composer participates in layout, so multiline text and OCR cannot cover the message viewport. The welcome screen shares the same mounted composer.
- Tools have a fixed header entry. Dialogue outline docks in 296px only from 1400px viewport width; smaller viewports use an overlay with Escape, focus restoration and keyboard containment.
- Phones: a four-item bottom navigation directly opens AI conversation and all three career pages. The sidebar remains an overlay for training, history and secondary pages. Navigation is inert under the sidebar and yields its space to the software keyboard. Safe-area padding and the visual viewport keep the composer visible; mobile input text is at least 16px to avoid iOS focus zoom.
- Algorithm editing retains its resizable problem/editor/console layout. Career pages use a restrained title/summary row above the working surface. Upcoming schedules precede the entry form; 新增安排 scrolls to and focuses the form. Recruitment shows search, city and job direction first, with remaining filters behind 更多筛选 and an active-count badge. Wide screens show every action; medium screens use grouped records instead of clipped table columns.

## Motion

- Controls: 90–150ms feedback. Panels: 180–220ms. Welcome/composer shift: 280ms.
- Sidebar changes reflow the main panel once, then animate its position for 180ms. Rapid toggles cancel the previous animation.
- Composer relocation uses measured start/end positions and a compositor transform, rather than animating layout every frame.
- Plain streaming text appears in short phrases with 160ms opacity transitions from 60% opacity and a 100ms bounded flush. No text blur.
- Pending blocks keep their final identifiers. Headings, lists, quotes and tables are formatted during generation; finalized blocks do not re-enter.
- Older revealed phrases are compacted to bound DOM growth. Actual content height drives a damped animation-frame scroll follower; any upward wheel intent suspends following immediately, including near the bottom. Committed streaming blocks are memoized at the subtree root.
- Respect reduced motion, composition input and keyboard focus. Generating a reply does not block drafting the next question.

- Career route code is prefetched during idle time and on navigation intent. Prefetch never requests personal data and respects data saver. Existing lazy loading remains for heavy editors.
- Avoid `transition: all` on active shared controls; only color, border, shadow, opacity and transform transition. Page changes do not queue behind animations.

## Function inventory

AI conversation (history, batch deletion, model/prompt/thinking selection, OCR, voice, stop, copy, regenerate, Markdown export, outline); interview setup/session/history/report; algorithm catalog, drafts, run/submit/review; recruitment filters and links; applications; schedule and screenshot intake; knowledge upload/context; authentication; admin users/server; standalone code, Markdown and resume editors; HTML preview and contact entry.

## CSS ownership

- Palette and Element Plus variables live in `theme.css`; lazy-loaded component defaults must not override them.
- Sidebar geometry belongs to `sidebar.css`; page components own their own surfaces and foregrounds. No global light-theme button overrides.
- Muted text still needs readable contrast. Pale emphasis surfaces use darker ink in light mode and lighter ink in dark mode.

## Verification

- Unit and component regression: `npm test`.
- Production compilation: `npm run build`.
- Browser audit: start Vite, install/provide Playwright, then `node scripts/ui_audit.cjs`. `NODE_PATH` may point to an existing Playwright installation; `PLAYWRIGHT_BROWSER_EXECUTABLE` selects an installed browser. API fixtures are isolated from production.
- The browser audit covers 1440, 1024, 390 and 320px viewports, all main routes, sidebar/outline geometry, multiline input, IME Enter, live streamed chunks, reading-position preservation, stopping, drafting, discarding a stream, route changes followed by resize, light/dark surfaces and reduced motion. Standalone editors are checked at desktop and mobile widths; use `UI_AUDIT_SCOPE=editors` to rerun only those checks.
- Review screenshots in `frontend/test-results/ui-audit`. These are generated audit evidence, not application content.

- Additional quality audit: `STRICT_CONTRAST=1 node scripts/ui_quality.cjs`. It measures rendered text contrast in both themes, checks sidebar space, preserves list position across folding, tests picker search/keyboard/permissions exposure, and checks narrow-screen Escape behavior. This covers tested states, not a blanket accessibility certification.

- Career navigation audit: `node scripts/ui_navigation.cjs`. Checks one-click access at 1440/1024/390/320px, populated schedules, both themes, recruitment actions, filter expansion, rapid switching, keyboard viewport, composer clearance and Cmd/Ctrl+K. Timing output uses local fixtures and is not a production latency guarantee.
- Design references and adversarial findings: [REFINEMENT.md](./REFINEMENT.md).
