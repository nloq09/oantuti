# Validation back navigation and state synchronization

Implemented 2026-10-22 for `PROMPT-FIX-VALIDATION-BACK-NAVIGATION-STATE-SYNC.md`.

## Exact root cause

`FinalValidationWorker` emits **PASS / FAIL** for individual steps, while the overall validation uses **PASSED / FAILED**. The participant-flow aggregate already returns overall status and all step details together. `final-validation-view.js` counted only steps equal to PASSED and treated absent/unrecognized steps as pending/unknown. Therefore a genuine server response with overall PASSED and eight PASS steps displayed “completed” plus 0/8 and “Chờ cập nhật”. Earlier client fixtures used PASSED steps and did not reproduce the actual contract.

Reading a source version does not reset the backend steps. The API regression now verifies all eight PASS entries remain identical after reading the immutable source and requesting participant-flow again. This is not repaired by setting the count to eight whenever overall status is PASSED.

No validation state is persisted in localStorage/sessionStorage by these pages. The source link already carried tournament/version context and its back link already targeted tournament detail; routing is now expressed through the existing canonical route helper. Requests use no-store.

## State and refresh

- `validation-detail-state.js` defines the server step codes and normalizes PASS/FAIL (also accepting the former client aliases). It exposes IDLE, LOADING, READY and ERROR.
- Absent/null details mean LOADING. Explicitly empty, incomplete, unknown or contradictory step maps mean ERROR. Overall PASSED with any non-pass step is `VALIDATION_STATE_INCONSISTENT`, not a successful card.
- Counts come from actual normalized server entries, with null counts while unavailable. No default eight-row pending result is rendered. Inconsistent data reports only the diagnostic code, without source or identifiers.
- `player-view-state.js` shares this model with the renderer. Incomplete data cannot produce WAIT_CHECK_IN or a fake final success. Timeline registration checks incomplete validation before rendering a check-in screen.
- `refreshPlayerTournamentState()` reuses the existing refresh queue. Page mount still fetches summary, personal state and the aggregate flow before one render. No separate validation-detail fetch or competing cache was introduced.
- The existing queue already handles persisted pageshow/BFCache, visibility, epoch fencing, aborts and timer suspension. Those handlers are reused, not duplicated. On resume/visibility/popstate the validation display is marked LOADING until a fresh coherent response arrives.
- A popstate listener restores the supported tab and refreshes; a different tournament ID requires a new page mount. Its listener is removed on a nonpersisted pagehide.
- Forced refresh replaces an older in-flight read. Even if a mocked/network transport ignores abort, the existing isCurrent/epoch check rejects its late response.
- Failed refresh shows a retryable error, not 0/8. Request-state changes participate in repaint comparison, so a subsequent successful poll can recover even at the same tournament revision.
- The same aggregate poll remains necessary to discover phase/check-in changes after a terminal validation; no additional validation polling interval was created. Source-page header timers now pause/restart correctly around visibility/BFCache.

## Files

New:

- `client/js/tournament/validation-detail-state.js`.
- `client/tests/player-validation-back-navigation.test.js` (contract, loading/empty/inconsistent states, real module navigation/refresh/race/error/BFCache integration).
- This report.

Changed:

- `final-validation-view.js`: normalized details, readiness guard, loading/error/retry presentation, unavailable counts remain null.
- `player-view-state.js`, `player-state.js`, `player-shell.js`: coherent validation/next-action state and request-state repaint.
- `player-detail.js`: named unified refresh, resume/loading/error, replaceable forced reads, popstate cleanup.
- `source-view.js`: canonical return route and resumable timer.
- `timeline-registration.js`: refresh callback and incomplete-validation guard.
- `organizer-validation-view.js`: the same PASS/FAIL normalization fixes step counts/labels there as well; no dashboard redesign.
- Existing client validation, next-action, frozen-controller, read-recovery and final-flow tests now use the actual server vocabulary and complete step fixtures where required.
- `FinalValidationApiTest.java`: real worker/API contract regression through source read and return to participant-flow.

No production backend code, API/DTO, database, worker execution policy or GameEngine changes.

## Verification

- **406 client tests PASS, 0 FAIL, 0 SKIPPED**.
- **1 focused backend API integration PASS**, using the real FinalValidationWorker and immutable-source endpoint. Verifies eight server PASS entries and unchanged results after source read.
- Real page modules tested with mocked transport/DOM: validation → source → canonical back; loading during delayed BFCache refresh; version 2 response wins over delayed version 1; error + retry; repeated persisted hide/show performs exactly one participant-flow read per restoration.
- Server-step normalization also checked against the Organizer card.
- Browser E2E / desktop/mobile screenshots: **SKIPPED**. Browser tooling previously rejected URL authority for the local app. No alternative browser/CDP workaround was used. DOM/controller tests are not a claim of browser or visual verification.
- Local ignored logs: `server/build/local-run/validation-navigation-client.txt`, `validation-navigation-backend.txt`, `validation-navigation-build.txt`.
