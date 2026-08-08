# Contributing to SPARK

## Branching model

- `main` — stable. Only ever receives merges from `dev`, and only when `dev`'s CI is green.
- `dev` — integration branch. All feature/fix work targets this branch.
- `feat/<name>` — a feature, branched from `dev` (e.g. `feat/purse-topup`).
- `fix/<issue>` — a bugfix, branched from `dev`, named after the issue it closes (e.g. `fix/123`).

## PR flow

1. Branch from `dev`: `feat/<name>` or `fix/<issue>`.
2. Make your change, with tests, scoped to one component (`android/`, `backend/`, `dashboard/`)
   or `docs/` where possible.
3. Open a PR **against `dev`** (not `main`).
4. At least 1 approving review is required before merging.
5. Squash-merge into `dev`.
6. Periodically, once `dev`'s CI is green, `dev` is merged into `main`. Nobody commits to `main`
   directly.

If your change touches a shared contract in `docs/`, call that out explicitly in the PR
description and get sign-off from every workstream the contract affects before merging.

## Code style

- **Android (Kotlin)**: follow the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html);
  format with `ktlint`/Android Studio defaults before committing.
- **Backend & Dashboard (TypeScript)**: format with Prettier, lint with ESLint; no `any` without
  a comment explaining why it's unavoidable.
- Keep functions small and single-purpose. Prefer explicit code over clever code.
- No commented-out code, no debug prints left in committed code.
- New code touching signing/serialization must include a test vector cross-checked against
  [docs/canonical-serialization.md](docs/canonical-serialization.md).

## Commit message format

```
<type>(<scope>): <short summary>

[optional body]

[optional footer, e.g. Closes #123]
```

- `type`: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`
- `scope`: `android`, `backend`, `dashboard`, `docs`, or omitted for repo-wide changes
- Summary: imperative mood, no trailing period, ≤72 chars

Examples:
```
feat(android): add purse token top-up flow
fix(backend): reject transactions with stale device certs
docs: define canonical serialization rules for signed payloads
```

## Repo admin setup (one-time)

For now, PRs require manual review only — at least 1 approving review before merging to `dev`.
We'll add CI and branch protection rules in week 2 once integration begins.

## Maintainers

| Member | Area | Name | Email |
|---|---|---|---|
| Member A | Android (wallet) | Akshayathiru | akshayat.it2024@citchennai.net |
| Member B | Backend | Samiksha | trsamiksha.it2024@citchennai.net |
| Member C | Dashboard / AI | Faleesha-Zaeen | faleeshazaeenzarshad.it2024@citchennai.net |

Shared contracts (`docs/`) are jointly owned by all three — see the PR flow above for the
sign-off requirement when a `docs/` change affects more than one workstream.

> Fill in names and emails before opening the repo up to outside contributors.
