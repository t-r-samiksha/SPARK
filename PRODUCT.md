# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

React 18 + TypeScript + Vite (user-specified). Hand-rolled component system; no UI kit, no
chart library, no CSS framework. Self-hosted fonts via @fontsource (no runtime Google Fonts
dependency — consistent with an offline/security posture).

## Users

- **Primary:** SPARK operations/admin staff — the operator at the console. Technical, security-
  literate people responding to incidents (double-spend attempts, compromised devices), toggling
  disaster mode during outages, and reviewing offline spend-cap intelligence. They act under
  time pressure and must be able to trust what they see at a glance.
- Second audience: the SPARK engineering team (Members A/B/C) using the console to observe the
  system they built, and auditors reviewing incident history.

## Product Purpose

The SPARK administrative control dashboard. One surface from which an operator monitors security
incidents, revokes compromised device certificates, controls disaster mode for regions, and
inspects recommended offline spending limits. Success means the operator can understand system
state and act correctly and deliberately — including choosing *not* to act — under pressure.

## Positioning

An operations console for an offline-payment trust network: incidents, device trust, and
spend-cap risk are *real system state* backed by signed cryptographic artifacts, not marketing
metrics. No metric, count, or recommendation on this surface is invented; when the backend does
not supply a value, the console says so rather than fabricating it.

## Operating Context

- One authenticated operator at a desktop/tablet console; sessions may also run on a laptop and
  be viewed on smaller screens.
- The console talks to the SPARK backend over HTTP (admin endpoints use the `X-Admin-Key` shared
  secret; read endpoints use bearer session tokens — see Capabilities and Constraints).
- Operators work with UUIDs, epoch timestamps, and integer-paise amounts (docs/id-conventions.md).
- Incident response is the primary ritual: watch the feed, expand details, revoke a device,
  coordinate with the wider team.

## Capabilities and Constraints

Confirmed features (Phase 1 + 2 scope):

- Incident feed: `GET /api/v1/admin/incidents?type=double_spend|fraud_flag|all`, with filtering,
  expandable details, refresh, and honest empty/error/loading states.
- Device revocation: `POST /api/v1/admin/revoke` `{device_id, reason}` with a deliberate
  validation → confirmation → processing → outcome sequence.
- Disaster mode: `POST /api/v1/admin/disaster/toggle`
  `{region_geo, type, enabled, higher_cap?, essential_only?}`.
- Recommended cap: `GET /api/v1/limit/recommendation` — **backend currently returns 501 Not
  Implemented**; the console must present an intentional unavailable state, never a fabricated
  number. (Real Member C cap model is a later phase.)

Constraints:

- Admin endpoints authenticate via `X-Admin-Key` header; the dashboard reads the key from
  `VITE_ADMIN_KEY` (env). No real secret is committed.
- **No admin GET endpoint exists to list active disaster events** — the console tracks the
  last-known state from its own toggle responses and labels it as local state. A backend
  `GET /admin/disaster` (or session-capable read) is a reported dependency.
- Do not modify `android/` (Member A) or `backend/` (Member B) in this phase.
- Amounts are integer paise; timestamps are Unix epoch seconds; IDs are canonical UUID v4.
- `fraud_flag` incidents are always empty today (no fraud intelligence yet — Member C later
  phase); the empty state must teach this rather than pretend.

## Brand Commitments

- Name: SPARK. The console is referred to as the SPARK operations console.
- Product framing (binding from the brief): high-trust, technical, precise, operational,
  security-oriented, calm under pressure, premium, intentional, information-dense without
  clutter. Explicitly *not* a startup landing page, crypto dashboard template, generic SaaS
  admin panel, or "collection of cards".
- Motion is purposeful (communicates hierarchy, state, causality, continuity) and respects
  `prefers-reduced-motion`.
- Anti-AI-slop: no purple/blue gradients, glassmorphism, floating rounded cards, gradient text,
  neon styling, or decorative charts.

## Evidence on Hand

- Real API contract: `docs/api-contract.md` (endpoint shapes, auth, errors).
- Real backend implementation in `backend/src/` (Member B) — response shapes verified against
  source (e.g. `admin/routes.ts`, `purse/limitStub.ts`, `purse/routes.ts`).
- Real value conventions: `docs/id-conventions.md` (UUIDs, epoch seconds, paise).
- No incident data is fabricated; empty/501 states are shown truthfully.

## Product Principles

1. Truth over completeness: never invent an incident, a cap, or a system state.
2. Calm precision: dense, scannable, restrained; the interface disappears into the task.
3. Deliberate destructive action: revocation and disaster controls require explicit, informed
   confirmation.
4. Continuity of state: every state change is communicated (loading, empty, error, success,
   unavailable) without drama.
5. Earned familiarity: standard patterns where they exist, distinctive where it matters.

## Accessibility & Inclusion

- Keyboard navigable; visible focus states; semantic HTML; sufficient contrast (WCAG AA);
  `prefers-reduced-motion` collapses spatial motion to opacity crossfades.
- Form errors are explicit and linked to their fields; destructive confirmation is accessible.
