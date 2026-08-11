# SPARK Operations Console — Design System

> High-Security Financial Operations Console for an Offline-Payment Trust Network.
> Daylight light surfaces, deep verdigris/crimson/amber semantics, and cryptographic precision.

## Direction

- **Mode:** Operate & Defend (The operator acts under time pressure: monitor, analyze, authorize, revoke).
- **Physical Scene:** The network's ops room under office light — a calm daylight command console. Crisp
  cool grey-blue canvas (`oklch(96.6% 0.009 250)`), near-white card surfaces, slate ink, precise hairlines,
  and soft diffuse shadows. Light is chosen from the use scene, not the category.
- **Color Strategy:** Restrained cool neutrals with semantic-only chromatic accents, deepened for contrast
  on light grounds:
  - **Trust / Verified / Focus:** Deep Verdigris Teal (`oklch(46% 0.12 178)`).
  - **Double-Spend / Compromise / Danger:** Signal Crimson (`oklch(50% 0.19 27)`).
  - **Disaster Mode / Emergency Broadcast:** Hazard Ochre Amber (`oklch(58% 0.15 70)`).
  - **Telemetry / Network Intelligence:** Cobalt Cyan (`oklch(50% 0.12 240)`).
- **Surface language:** Light panels with 14px card radii, subtle engineering grid on the canvas, inset
  data wells, ambient radar scanning, and clear cryptographic visual hierarchy. Elevation is declared
  once per surface — a hairline border or a soft shadow, never both competing.

## Type System

- **UI & Display:** Schibsted Grotesk (400, 500, 600, 700) for all headings, navigation, buttons, field labels, descriptions, and operational controls.
- **Cryptographic Data & Telemetry:** JetBrains Mono (400, 500, 600, 700) for UUIDs, transaction identifiers, purse tokens, epoch timestamps, paise amounts, and code references.
- **Scale:** 10px (micro), 11px (overline), 12px (table headers), 13px (compact labels), 14px (body/inputs), 18px (titles), 22px (component headers), 28px (telemetry readouts).

## Motion Language

- **System Health:** Ambient breathing beacons and live ping metrics.
- **Incident Arrival & Spatial Continuity:** Staggered row entrances with spring-like deceleration; non-matching filter items fade smoothly while matching items preserve coordinates.
- **Forensic Inspector Sheet:** Silky `grid-template-rows: 1fr` accordion animation revealing transaction collision diff matrices without breaking spatial continuity.
- **Disaster Mode Sequence:** Multi-stage workflow (Configure → Pre-flight Authorize → Broadcast Wave) with environmental state shifts.
- **Device Revocation Workflow:** 3-stage validation with UUID v4 syntax checking, audit reason presets, challenge matching, and CRL receipt emission.
- **Reduced Motion:** `@media (prefers-reduced-motion: reduce)` collapses spatial translation into instant/subtle opacity crossfades.

## API Truth & State Integrity

- Real Fastify backend endpoints:
  - `GET /api/v1/admin/incidents?type=...`
  - `POST /api/v1/admin/revoke`
  - `POST /api/v1/admin/disaster/toggle`
  - `GET /api/v1/limit/recommendation` (returns 501 - presented with honest architecture blueprint and ₹2,000 baseline fallback)
  - `GET /health` (server root ping with latency ms tracking)
- The console never fabricates values: every metric, count, or recommendation is either supplied by the
  backend or shown with an honest empty/error/unimplemented state.
