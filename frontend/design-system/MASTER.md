# XZM Interview Helper — Mist / V5

界面围绕输入、阅读、练习和求职记录组织。所有既有业务能力保留，视觉层采用中性画布与雾蓝强调色。

## Theme

- Canvas `#F8F9FC`; sidebar `#F1F3F8`; paper `#FFFFFF`.
- Text `#252D3D`; secondary `#566176`; muted `#5E6B80`.
- Accent `#486494`; active surface `#DCE7FA`.
- Dark canvas `#141820`; dark paper `#1F2734`; accent `#A8BEE9`.
- Use `tokens.css` and `theme.css` semantic variables. Algorithm surfaces bridge to these same tokens; Monaco's editor theme is matched separately.
- Success, warning and danger are semantic states, not decorative brand colors.

## Layout

- Desktop sidebar: 224px / 64px. Three frequent workspaces stay visible, with the selected extra workspace retained. A searchable picker exposes every permitted workspace. History and problem catalog use the remaining height and preserve their state when the rail folds.
- Main headers: calm, consistent hierarchy. Chat reading width: at most 800px.
- Chat composer participates in layout, so multiline text and OCR cannot cover the message viewport. The welcome screen shares the same mounted composer.
- Tools have a fixed header entry. Dialogue outline docks in 296px only from 1400px viewport width; smaller viewports use an overlay with Escape, focus restoration and keyboard containment.
- Phones: sidebar overlays instead of pushing content. Safe-area padding and dynamic viewport units support available screen height.
- Algorithm editing retains its resizable problem/editor/console layout. Career tables and forms retain their task-specific density.

## Motion

- Controls: 90–150ms feedback. Panels: 180–220ms. Welcome/composer shift: 280ms.
- Sidebar changes reflow the main panel once, then animate its position for 180ms. Rapid toggles cancel the previous animation.
- Composer relocation uses measured start/end positions and a compositor transform, rather than animating layout every frame.
- Plain streaming text appears in short phrases with 220ms opacity transitions and a 100ms bounded flush. No text blur.
- Pending blocks keep their final identifiers. Headings, lists, quotes and tables are formatted during generation; finalized blocks do not re-enter.
- Older revealed phrases are compacted to bound DOM growth. Actual content height drives a damped animation-frame scroll follower; any upward wheel intent suspends following immediately, including near the bottom. Committed streaming blocks are memoized at the subtree root.
- Respect reduced motion, composition input and keyboard focus. Generating a reply does not block drafting the next question.

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
