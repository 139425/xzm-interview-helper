# XZM Interview Helper — Visual System V4

## Thesis

The product is a candidate command center: paper-like working surfaces sit on a calm field, deep ink-green establishes focus, and electric mint appears only where progress or the next action matters.

## Tokens

- Ink: `#132522`
- Field: `#F3F6F2`
- Paper: `#FFFEFA`
- Brand: `#0B6B64`
- Brand strong: `#074842`
- Progress signal: `#DDFC74`
- Warning: `#C87A10`
- Danger: `#D64A44`

Use semantic variables from `src/assets/tokens.css` and `src/assets/theme.css`; page components should not invent a parallel palette.

## Typography

- Display: `--xzm-font-display` for high-impact Chinese headlines and major milestones.
- Interface: `--xzm-font-sans` for controls and reading text.
- Data: `--xzm-font-data` for stages, counts, timers, and utility labels.

## Layout and interaction

- The application shell is operational, not a marketing landing page. The real work stays in the first viewport.
- Progress is ordered from the furthest stage back to unsubmitted; recent update time only breaks ties.
- Mobile tables become readable cards instead of relying on horizontal scrolling.
- Controls target at least 44px on touch screens, expose visible focus, and respect reduced motion.
- Motion is limited to 150–300ms transitions and one orchestrated page entrance.

## Signature

The progress signal is a thin electric-mint marker used for Offer, active route identity, and the next action. It should remain rare enough to retain meaning.

## Anti-default check

- No purple gradient, floating glass-orb composition, generic KPI hero, or emoji navigation icons.
- Cards are used only for real grouped objects or data states.
- Decorative labels must encode product state, sequence, or provenance.
