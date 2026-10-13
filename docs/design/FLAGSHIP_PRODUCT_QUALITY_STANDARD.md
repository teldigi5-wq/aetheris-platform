# Flagship Product Design Quality Standard — Aetheris Creative Studio

**Audience:** product designers, frontend engineers, animation specialists, security reviewers and product QA.
**Status:** review guidance and limited automated checks; **not** WCAG certification or a claim that a production site was updated.
**Provenance:** principles from the owner's *Design Flagship Experiences* direction and the archived Wayfarer design master brief. The private ChatGPT transcript titled “Design Flagship Experiences” was **not fully retrievable**, so unobserved updates have not been guessed.

## Product philosophy

Build original, useful, accessible, maintainable experiences where content and working product behavior matter more than decorative effects. Prefer **cinematic editorial polish** over generic dashboards, exaggerated glass, bright neon, gratuitous 3D orbit animations or overloaded SaaS cards.

Every interface must communicate what is **working, estimated, disabled, simulated, live, stale or waiting for permission**. Attractive agent progress graphics do not count as real execution. No fabricated booking inventory, affiliate arrangements, partner pricing, users, ratings, testimonials, or hardware validation.

## Global design system fundamentals

| Discipline | Enforced principle | Manual review |
| --- | --- | --- |
| Color | Named semantic tokens: canvas, raised surface, primary/secondary text, accent, functional focus/error/success and subtle decorative stroke; never rely on color alone. | Inspect every adjacent surface, overlays, disabled states, transparency and actual display conditions. |
| Color contrast | WCAG 2.2 AA normal text ≥4.5:1, large text ≥3:1 and essential non-text UI affordances ≥3:1. | Eye-check all media overlays and semantic boundaries; static theme pairs are only a subset. |
| Typography | Clear heading/body/caption roles; use editorial serif displays selectively, sans-serif for controls; 16px baseline body and legible 14px minimum supporting UI when possible. | Test font fallback, localization, reading line length, text expansion, bold hierarchy and up to 200% text resize. |
| Rhythm | Shared 4/8px-based spacing, measured grid alignment, generous but purposeful breathing room; use content-driven breakpoints. | Inspect 320, 360, 390, 768, 1024 and 1440 CSS-pixel widths, landscape orientation and zoom. |
| Controls | Target 44×44 CSS pixels when practicable (studio design convention). WCAG 2.2 AA 2.5.8 normally requires 24×24px with specified exceptions. | Keyboard-only task completion, focus visibility, labels, touch, drag alternatives, screen-reader workflows. |
| Motion | Use duration scales near 120/220/380ms for micro-feedback/transitions/emphasis; keep essential information available without movement. | Repeated playback, interaction interruption, reduced-motion alternatives, accidental auto-scrolling, device thermals and FPS. |
| Imagery | Source-appropriate photography and brand-authentic visual language; optimize resolution, subject framing, cropping, alt text and loading. | Verify rights, genuine evidence, responsive focal points, no unsupported generated claims. |
| Feedback | Honest skeleton/loading/error/offline/success states; preserve user input on recoverable failures. | Failure recovery, aria-live behavior, asynchronous race and canceled requests. |
| Engineering | Design tokens, composable components, semantic markup, progressive enhancement, minimal dependencies, safe output encoding. | Real Chrome/Firefox/Safari/mobile tests, Lighthouse/Web Vitals, unit/E2E and security reviews. |

### Accessibility boundaries

WCAG 2.2 AA is an **acceptance target**, not an automatically achieved guarantee. Check landmarks, meaningful heading structure, tab and focus order, form guidance, keyboard-equivalent gestures, text resizing, non-text contrast, contrast over images, responsive reflow, touch target size/spacing, error identification, timing, motion, language and assistive technology.

Automated static color-ratio checks cannot certify WCAG. A design may pass every palette check and still fail keyboard use, text reflow, translated copy, focus or screen reader behavior.

Reference: https://www.w3.org/TR/WCAG22/ .

## Motion and motion-graphics quality

- Motion must explain state or reinforce a purposeful product story, not distract from a task.
- Avoid page-wide parallax on scroll, unbounded loops and content pinned so long that it blocks navigation.
- If auto-moving content runs longer than five seconds and is presented alongside other content, provide an appropriate pause/stop/hide mechanism when the applicable WCAG criterion requires one.
- Respect `prefers-reduced-motion` throughout, including scroll, 3D rotation and canvas render loops.
- Performance budgets are **targets**, not measured claims; verify on actual low-powered phones and battery operation.
- Avoid importing GSAP, Rive, Lottie, Blender, Three.js or new native runtimes until licensing, source provenance, code review and owner approval are satisfied.

## Wayfarer flagship experience adaptation (reference specification, separate repository)

The Wayfarer website and Vercel project are separate from Aetheris. This section encodes product requirements as review guidance; this PR does **not** write, install into, or redeploy Wayfarer.

### Brand palette (user's existing travel-design direction)

| Token | Reference value | Usage |
| --- | --- | --- |
| `midnight-navy` | `#071421` | Cinematic navigation, image storytelling |
| `deep-ocean` | `#0B2133` | Dark sections and overlays |
| `glacier-white` | `#F6F8FA` | Task-heavy booking panels and high-contrast surfaces |
| `electric-teal` | `#21C6B7` | Selective highlights, active navigation, progress |
| `warm-sand` | `#E8D6B2` | Editorial warmth, accent on dark surfaces |
| `cool-slate` | `#8293A5` | Secondary brand graphics; **do not assume usable text contrast** |
| `soft-coral` | `#FF806B` | Sparse accents or verified semantic cues, never color-only |

Semantic usage must be assigned through actual contrast tests (including hover and focus), not directly by token names. Neumorphism and glassmorphism are **selective effects** on search, segmented controls, contextual overlays, with legible foregrounds and visible boundaries.

### Three flagship experiences; one coherent customer journey

**1. Wayfarer Globe / Discover.** Real-world Earth with correct landmass proportions, atmospheric and realistic lighting, license-reviewed textures and truthful geolocation. Accessible interactive 2D map/list is mandatory on low-power or WebGL-unavailable devices; destination selection must hand off to planning. Avoid generic wireframe spheres and synthetic network-line globes.

**2. AI Journey Studio / Plan.** Real agent tool calls and evidence, not a decorative 'agent is thinking' loader. Distinct destination, climate, accommodation, transport, budget and itinerary responsibilities when actually supported. Preserve departure, destination, dates, travelers, currency, travel constraints, selected hotels, and arrival transport. Show timestamp/source and distinguish `provider-observed`, `estimated`, `AI-suggested`, `unavailable`, with edited itinerary versions, user control and budget breakdown. Never invent flights, partner rooms or live availability.

**3. Arrival Companion / Arrive, then Explore.** User-approved itinerary and arrival airport/hotel handoff, sourced transfer options, first-hour practical guidance, offline checklists, check-in readiness, nearby help and graceful fallback on stale or absent sources. Do not infer booking or payment confirmation from an itinerary draft.

**Journey states:** Discover → Plan → Arrive → Explore. This must be persisted and connected in the *actual Wayfarer application* before claiming end-to-end functionality; offline reference contracts in Aetheris are not proof of integration.

### Mobile is a first-class product

Mobile should not be a shrunken desktop hero. It needs thumb-friendly primary navigation, accessible and operable maps/lists, swipe/drag alternatives, consent and booking clarity, readable itinerary timelines and zero horizontal clipping at 320px. Account for screen reader/zoom and motion preference.

## Automated gate coverage in this PR

`ui/design-quality-gates.js` tests text, muted copy, elevated surfaces, eyebrow accent, CTA text and one focus/background contrast pair on predefined safe palette tokens. It flags possible long-copy reflow risks and subtly low-contrast decorative lines.

**Not tested by this gate:** actual CSS computed colors in every browser state; generated media contrast; real-pointer sizes, WebGL, focus order, dynamic motion and actual device metrics. Passing should always read **“static checks passed — manual audit required,”** never “WCAG certified,” “award-winning,” or “production ready.”

## Release and review checklist

1. Trace screen goal, journey continuity, truthful functional claims and no invented commercial partners.
2. Inspect tested semantic tokens, typography/spacing hierarchy, meaningful breakpoints, dark and light contrast.
3. Run accessibility and manual keyboard/screen-reader/zoom tests in actual browsers.
4. Test reduced motion, difficult network conditions, device thermal/performance, imagery credits and asset security.
5. Review source package licenses, API/provider terms, token secrets and cost caps.
6. Obtain owner authorization and satisfy protected-branch CI before deployment or activation.

Reference standards: [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/), [WAI Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum), [WAI Pause Stop Hide](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide).
