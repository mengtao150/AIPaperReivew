# Evidence workbench v1 implementation plan

> **For agentic workers:** Use superpowers:executing-plans. Execute inline and request one final review.

**Goal:** Turn a saved PubMed search into an evidence-linked topic map, review inventory, editable synthesis notes and commissioning cards.

**Architecture:** Add a workspace-scoped, revisioned JPA JSON document. A deterministic baseline provides multilabel themes, actual publication timelines, review matches and author evidence. Optional asynchronous AI synthesis uses the user's selected connection and a disclosed bounded corpus. Validate every model citation against the submitted source text before saving. Existing reports survive failed jobs.

**Tech stack:** Existing Java 17 / Spring Boot / Jackson / MySQL; Vue 3 / Element Plus / ECharts. No additional dependencies.

**Spec:** The seven-part design accepted in the preceding conversation. v1 covers the four analysis views and evidence provenance, using retrieved PubMed metadata and abstracts. No claimed full-text appraisal, external review novelty search, or automatic invitations.

## Global constraints
- No fabricated consensus, novelty, trend or coverage claims. Unverified means unverified.
- Scope and any sample limits are visible. Retracted papers are excluded and counted.
- Model calls are explicit; no automatic use of saved keys during development or page loading.
- Every AI note and coverage assertion has a PMID and an exact title/abstract quote in the submitted corpus.
- Persist edits per browser workspace and search. Optimistic revision conflicts return HTTP 409.
- Preserve original search, article, translation and contact workflows.

## Review focus
- Papers with no MeSH/abstract/date and searches containing no reviews remain usable without invented gaps.
- Unknown citations, invented quotes, malformed responses and model failures never replace a working report.
- Navigation while requests are pending cannot replace another task's screen.
- Regeneration requires explicit review and does not silently discard editorial decisions.
- Narrow screens and keyboard users can access topic selection, evidence and editing.

## Tasks
- [x] 1. Write failing backend tests for multilabel baseline, review identification, actual dates and citation validation; implement EvidenceBuilder and EvidenceValidator.
- [x] 2. Test revisioned persistence and failed AI job preservation; implement entity/repository/service/controller and disable legacy fabricated novelty checks.
- [x] 3. Implement the four-view workbench, graph, review matrix, evidence drawer, editing, author evidence, candidate decisions and Markdown export. Add browser regressions for evidence and persistence behavior.
- [x] 4. Run backend/frontend suites and builds, inspect desktop/mobile UI, request independent review and fix material findings. Update README and this ledger. Review availability limitation is recorded below.

## Progress / rulings
- Baseline frontend: 3 Node tests pass. Git baseline f40fae4 is clean.
- Ruling: work in the current checkout on codex/evidence-workbench-v1 so the user's existing localhost preview reflects the implementation; no worktree, push or merge.
- Ruling: AI output remains an abstract-based draft until editorial review. A baseline match between a review and a theme is not a finding of deep/partial coverage.
- Ruling: no real saved model key will be used by development checks; controlled provider responses exercise AI integration.
- Validation: 5 new browser scenarios pass against Chrome: map/evidence, edit persistence, full-text coverage verification, failed model startup, four narrow-screen views. No model credentials used.
- Live check: saved scan 16 yields 56 eligible papers, 56 type-identified reviews and 10 overlapping MeSH themes. 8081 backend restarted with the existing local database configuration; no secret echoed.
- Independent review was requested, but its worker ended at a service usage limit before issuing findings. Do not represent that as a completed review. Root performs the remaining code review and regression checks.
- Root review: added a failing regression for cross-topic citation mismatches, then enforced association checking. Added tests for bounded AI excerpts, invalid quote rejection without revision changes, separate review inventory and explicit human full-text coverage records. Guarded pending UI operations with a route epoch. No unresolved material finding identified in the reviewed paths.
- Final verification: backend 40 passed / 1 optional external PubMed test skipped; Node 6 passed; browser 20 passed including the opt-in real saved-corpus render check. Both builds pass. Vite reports the existing large main chunk advisory; no dependency changes introduced.
- Real API smoke: save/reload succeeds; stale revision returns409; fabricated quote returns400 and leaves revision unchanged; review inventory persists; a separate workspace starts at revision0. Source corpus remains56 papers. No actual model calls performed.
- UI inspection: desktop graph, review matrix and narrow-screen notes captured under .local/evidence-*.png and inspected. Graph uses matching direction numbers to avoid overlapping long labels.
- Delivery: retain changes in the current checkout on codex/evidence-workbench-v1; no commit, push or merge requested or performed. Running development backend is port8081; frontend remains port3000.

## Follow-up: analysis request timeout
- User encountered the gateway's exact HttpTimeoutException message. The analysis allowed up to70000 title/abstract characters and14000 output tokens while using the ordinary90-second timeout. Existing logs had no timeout telemetry, so provider queueing versus generation latency could not be separated.
- Added RED→GREEN request-policy tests: ordinary requests stay90 seconds; evidence requests use300 seconds by default. Timeout errors identify the applied budget. The analysis synthesis now explicitly uses this long-task entry point and caps output at8000 tokens with a compact prompt.
- Added safe request/timeout telemetry, job start/end times and deadline, and actual elapsed-time UI. No automatic retry and no real credential used by verification. Added a background-timeout preservation regression and a browser waiting-state regression.
- Follow-up verified: backend43 passed /1 optional PubMed test skipped; Node7 passed; browser21 passed; frontend and backend packages pass. Updated port8081 service and confirmed it serves the existing56-paper corpus. The user's actual upstream response time has not been measured; no real paid model request was sent by this verification.
- Diagnostic correction: HttpConnectTimeoutException subclasses HttpTimeoutException, so the original shared message could also mean that connection establishment timed out. Added a RED→GREEN regression and a separate connection-timeout branch. The90-second response budget is a confirmed application defect, but the exact phase of the user's earlier failure cannot be reconstructed from old logs. Final claims must preserve that distinction.
- Final timeout fix verification: backend44 passed /1 optional external PubMed test skipped; Node7 and browser21 passed. Both builds pass. Restarted port8081 with the final connection/response distinction and confirmed existing56-paper data is served. No actual model key used.

## Follow-up: actual model quotation validation failure
- User reported the generic validation rejection. Existing gateway logs confirmed their zai-coding/glm-5.3-flash response completed in96.4seconds; no original model output had been retained. Added safe code/path validation diagnostics without changing the underlying validity requirements.
- Ruling: for this user-requested production-connection debugging, reuse the already-configured backend connection for one reproduction and one post-fix verification, using the same20 retrieved public papers. The user was informed before each phase. Credentials were used only by the existing backend, never decrypted/echoed by shell code. This overrides the earlier no-live-model development-check ruling only for this explicit debugging session; no automatic retries were introduced.
- Reproduced with the same20 papers: model completed in81seconds, then quote_mismatch at $.topics[0].notes[0].citations[0].quote for PMID41634135. The source text and supplied quotation did not match; exact cause within the generated text was not assumed.
- Fix: immutable EvidenceCatalog assigns IDs to exact source slices; model returns evidenceId instead of copying quotations. Bind known IDs to server-owned originals, then run the strict validator. Unknown IDs/conflicting attached metadata still fail; legacy free quotes remain strictly validated. Only harmless structure/identifier normalization is applied; valid references reconstruct their topic/card index entries. No unsupported judgments are dropped or silently accepted.
- Post-fix actual20-paper call completed in75seconds and saved revision1 in the user's existing task16:6 topics,18 draft notes,1 candidate card. Independently compared all49 saved citations against current original records:0 mismatches. No need for the user to send a third model request.
- Backend suite50 passed /1 optional external PubMed test skipped; package built and running on8081. Frontend citation API remains compatible. Focused independent review requested for the new binding/validation paths.
- Focused independent review completed with no material findings; reviewer verified unknown IDs, conflicting metadata, legacy fabricated quotes and association reconstruction with synthetic inputs, without credentials or paid calls.
