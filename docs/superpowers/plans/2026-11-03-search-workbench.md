# Search Workbench Implementation Plan

> **For agentic workers:** Execute inline with superpowers:executing-plans. The user explicitly requested implementation in this workspace.

**Goal:** Deliver direct/AI-assisted PubMed search, readable bilingual article results, and durable user-managed model connections.

**Architecture:** Vue 3/Element Plus frontend; Spring Boot backend owns PubMed query compilation, compatible chat-completion calls, encrypted model settings and translation caching. Model settings are isolated using an opaque HttpOnly browser workspace cookie; this is local workspace isolation, not account authentication.

**Tech Stack:** Existing Vue/Vite, Java 17/Spring Boot; JUnit and Node tests.

**Spec:** User conversation: two search entry points sharing PubMed semantics, abstracts in results, Chinese title/abstract hover translations, user-configured DeepSeek/GLM-compatible providers with connection testing and persistent editable credentials.

## Global Constraints
- No model credentials bundled, logged, returned by APIs, or stored in browser storage.
- No fabricated AI outputs or connection-test success. No live provider call without user-configured credentials.
- User correction: offer independent BigModel/Z.ai Coding Plan presets as well as general endpoints; do not locally block them based on intended use. Preserve the selected endpoint, disclose provider rules and do not silently change billing channels.
- Preserve original English and explicitly label machine translation. Missing abstract remains missing.
- No changes to topic mining, expert sourcing or mail behavior.
- No git repository exists; work in place and retain a source backup. No account/multi-tenant commercialization claim.

## Review Focus
- A complete pasted query receives no hidden language/abstract/date filters.
- Changed conditions invalidate previous preview results and prevent fetching stale criteria.
- Saved credentials survive backend restart, blank edit preserves the key, endpoint/model edits invalidate test status.
- Failed/slow model calls, missing abstracts, pagination races and repeated hovers have useful recoverable states.
- Secrets and raw provider errors never reach logs/DTOs; custom endpoints reject local/private hosts and redirects.

## Tasks
- [x] 1. Add failing query/parser tests; implement canonical query compiler, ESearch diagnostics and persisted query metadata.
- [x] 2. Add failing model-vault/client tests; implement browser-scoped encrypted profiles, real connectivity tests, AI query assistant and cached translations.
- [x] 3. Implement shared visual system, dual-entry search, settings management, abstract table/hover translations/detail drawer and task navigation.
- [x] 4. Run backend tests, frontend tests/build, local runtime/browser checks; review and document deployment limits.

## Execution ledger
- Ruling: work in place because this directory has no .git; no worktree or commits can be created without initializing source control.
- Superseded ruling: the initial local Coding-only restriction was removed after the user reported it prevented their own connection test. Provider-side rules remain authoritative; the platform does not impersonate coding tools or bypass provider access controls.
- Tasks 1–3 implemented. Baseline had no automated tests. Added backend contract, vault, transport, AI workflow and optional live PubMed tests; Node state-contract and Playwright UI tests.
- Final review by independent reviewer: stale AI draft, route reuse retaining old search, interrupted worker status, endpoint-sensitive translation cache. Reproduced with failing tests, then fixed.
- Review ruling: endpoint-sensitive translation identity treated as important to maintain correct provider attribution after an endpoint edit; covered by a failing/passing identity test.
- Dependency audit surfaced existing Vite/ECharts advisories. Upgraded to Vite 6.4.4, plugin-vue 5.2.4 and ECharts 6.1.0; using chart modules on demand and testing old chart rendering. npm audit reports zero vulnerabilities.
- Ruling: package as reviewer-workbench.jar because an existing unknown Java process locks the old scout jar on Windows. Leave the existing 8080 service untouched; run this version on 8081 with the frontend proxy explicitly configured.
- Final verification: backend 17 tests (including the opt-in live PubMed test), Node 3 tests, Playwright 8 tests passed. Backend package and frontend production build passed. Real browser pages show no page errors, credential field is enabled, and narrow search/settings layouts have no document overflow. Live DeepSeek/GLM cannot be verified without user-provided credentials; settings has real test-and-save behavior and failures are covered by controlled responses. No deferred review findings.
